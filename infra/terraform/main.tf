# =============================================================================
# PRODUCAO — descricao da infraestrutura que JA EXISTE (FABIANO-10)
# =============================================================================
# Este arquivo nao cria nada. Ele descreve o que ja esta na AWS para que o
# `terraform plan` volte a ser um detector de drift: rodou e deu "No changes",
# ninguem mexeu pelo console.
#
# REGRA QUE NAO SE NEGOCIA (do card): se o plan mostrar diferenca, a correcao vai
# NESTE arquivo para bater com a realidade — nunca o contrario. Rodar `apply`
# contra um plan nao revisado, com o RDS de producao no meio, transforma
# arrumacao de casa em incidente.
#
# Os enderecos dos recursos (aws_instance.app, aws_eip.app, ...) sao os mesmos
# do arquivo original de proposito: os comandos de import ja preparados no card
# apontam para eles.
# =============================================================================

# -----------------------------------------------------------------------------
# Key pair SSH
# -----------------------------------------------------------------------------
resource "aws_key_pair" "deployer" {
  key_name   = "${var.project_name}-key"
  public_key = var.public_key_material

  lifecycle {
    # A AWS nao permite trocar a chave de um key pair existente: alterar recria.
    # Recriar invalidaria o acesso SSH a maquina de producao — e ninguem
    # descobriria ate a proxima vez que precisasse entrar, que costuma ser
    # exatamente durante um incidente.
    ignore_changes  = [public_key]
    prevent_destroy = true
  }
}

# -----------------------------------------------------------------------------
# EC2 da aplicacao
# -----------------------------------------------------------------------------
# Esta e a maquina ANTIGA (i-0987e63c336e202b9), que atende o cliente hoje.
# A maquina nova do blue-green (i-008f8d272588845ef) NAO e gerenciada aqui de
# proposito — ela sera substituida pelo Auto Scaling (modulo ./asg), e importar
# uma instancia solta para depois remove-la do codigo significa um `destroy` no
# plan. Blue-green se faz com duas coisas coexistindo, nao com uma virando outra
# dentro do state.
resource "aws_instance" "app" {
  ami                    = var.ami_id
  instance_type          = var.instance_type
  key_name               = aws_key_pair.deployer.key_name
  vpc_security_group_ids = [aws_security_group.ec2.id]

  # Identidade da maquina (FABIANO-20). Associar perfil a uma instancia que ja
  # existe e alteracao NO LUGAR — nao recria, nao reinicia, nao derruba o
  # cliente. E o que destrava a copia do backup no S3 e, depois, a leitura dos
  # segredos no Parameter Store.
  #
  # Ate hoje esta maquina rodava com AWS_ACCESS_KEY_ID e AWS_SECRET_ACCESS_KEY
  # escritas no .env do disco. Com o perfil no ar, essas duas linhas podem ser
  # apagadas: credencial que a AWS entrega e rotaciona sozinha e melhor que
  # chave permanente guardada em arquivo dentro de um servidor exposto.
  iam_instance_profile = aws_iam_instance_profile.instancia.name

  # O conteudo real do user_data que rodou no primeiro boot nao e recuperavel de
  # forma confiavel, e o arquivo do repositorio foi editado depois. Declarar o
  # arquivo aqui faria o plan comparar hashes diferentes e propor RECRIAR a EC2
  # de producao. Ver o bloco lifecycle abaixo.
  user_data                   = file("${path.module}/user_data.sh")
  user_data_replace_on_change = false

  root_block_device {
    volume_size = 8
    volume_type = "gp2"
  }

  tags = {
    Name    = "${var.project_name}-ec2"
    Project = var.project_name
  }

  lifecycle {
    # As duas armadilhas mapeadas no card, viradas em duas linhas:
    #
    #   ami       -> ForceNew. A instancia roda Amazon Linux 2; qualquer troca
    #                (inclusive para o AL2023 "correto") recria a maquina.
    #   user_data -> ForceNew. O arquivo divergiu do que rodou no primeiro boot.
    #
    # Sem estas duas, o primeiro plan depois do import propoe destruir e recriar
    # a EC2 que atende o cliente. Com elas, propoe nada — que e o certo, porque
    # o objetivo aqui e descrever, nao mudar.
    ignore_changes = [ami, user_data]

    # Rede de seguranca dupla: mesmo que alguem remova o ignore_changes acima
    # sem entender o motivo, o destroy e recusado com erro explicito.
    # Remover conscientemente e um commit proprio, revisavel.
    prevent_destroy = true
  }
}

# -----------------------------------------------------------------------------
# Elastic IP de producao
# -----------------------------------------------------------------------------
# 100.30.35.83. E o endereco que o DNS de producao aponta e o que a virada
# (FABIANO-47) move para a maquina nova. Destruir aqui significaria perder o
# endereco para a AWS — e ele nao volta.
resource "aws_eip" "app" {
  instance = aws_instance.app.id
  domain   = "vpc"

  tags = {
    Name    = "${var.project_name}-eip"
    Project = var.project_name
  }

  lifecycle {
    # ignore_changes em 'instance' e a correcao mais importante deste arquivo.
    #
    # Este recurso declara o EIP apontando para aws_instance.app, que e a maquina
    # ANTIGA (i-0987e63c336e202b9). Desde a virada de 08/08 (FABIANO-47) o
    # endereco esta na maquina NOVA (i-008f8d272588845ef), que nao e gerenciada
    # aqui de proposito — ver o comentario do aws_instance.app acima.
    #
    # Sem esta linha, um `terraform apply` REASSOCIARIA o EIP de volta para a
    # maquina antiga. Producao cairia no mesmo segundo, por um plan que ninguem
    # leria com atencao porque "so mexia em tag". O prevent_destroy nao protege
    # disso: nao e destroy, e update no lugar.
    #
    # Sai daqui quando o Auto Scaling (FABIANO-57) assumir o endereco.
    ignore_changes  = [instance]
    prevent_destroy = true
  }
}

# -----------------------------------------------------------------------------
# RDS MySQL de producao
# -----------------------------------------------------------------------------
# Os valores abaixo foram corrigidos para bater com a AWS. O arquivo anterior
# declarava backup_retention_period = 0 e skip_final_snapshot = true: um `apply`
# com aquilo teria DESLIGADO O BACKUP AUTOMATICO DO BANCO DE PRODUCAO e
# permitido apagar a instancia sem snapshot final. Nao era hipotese distante —
# recriar a instancia e exatamente o que se faz num desastre.
resource "aws_db_instance" "mysql" {
  identifier        = "${var.project_name}-db"
  engine            = "mysql"
  engine_version    = var.db_engine_version
  instance_class    = "db.t3.micro"
  allocated_storage = 20
  storage_type      = "gp2"

  db_name  = var.db_name
  username = var.db_user
  password = var.db_password

  publicly_accessible    = false
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = var.db_parameter_group

  # Realidade conferida em 05/08/2026 via describe-db-instances.
  backup_retention_period    = var.db_backup_retention
  backup_window              = "04:00-04:30"
  # Formato da AWS: ddd:hh24:mi-ddd:hh24:mi — o dia da semana vai nos DOIS
  # lados. O inventario do card registrou "mon:06:17-06:47", abreviado como
  # se escreve numa anotacao, e a abreviacao virou erro de sintaxe aqui.
  maintenance_window         = "mon:06:17-mon:06:47"
  deletion_protection        = true
  auto_minor_version_upgrade = true
  multi_az                   = var.db_multi_az

  # skip_final_snapshot = false e o oposto do que estava aqui. Com true, um
  # destroy apagaria o banco sem deixar copia nenhuma.
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project_name}-db-final-${var.ambiente}"

  tags = {
    Name    = "${var.project_name}-rds"
    Project = var.project_name
  }

  lifecycle {
    # A API da AWS nao devolve a senha do master. Depois do import o campo fica
    # vazio no state e o plan propoe "alterar" a senha para o valor da variavel
    # — que tambem esta vazio. Ignorar e o certo ate migrar para
    # manage_master_user_password, que tira a senha do state de vez.
    ignore_changes = [password]

    # O banco do cliente. Nao existe cenario em que destruir por Terraform seja
    # a acao certa; se um dia for, que seja removendo esta linha de propria mao.
    prevent_destroy = true
  }
}
