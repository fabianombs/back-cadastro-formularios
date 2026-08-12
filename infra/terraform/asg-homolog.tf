# =============================================================================
# Auto Scaling de HOMOLOGACAO — ensaio do FABIANO-57
# =============================================================================
# POR QUE HOMOLOGACAO PRIMEIRO
#
# O modo de falha deste modulo nao e uma maquina que nao sobe: e uma maquina que
# sobe, responde HTTP, passa no health check — e esta sem alguma peca que so se
# manifesta dias depois. Foi exatamente isso que a conferencia de 11/08 achou:
# o user_data instalava 'mariadb105', cujo mysqldump nao aceita
# '--set-gtid-purged', flag que o backup-db.sh usa. A maquina nasceria perfeita
# e o backup morreria as 3h30.
#
# Ensaiar aqui custa 20 minutos. Descobrir em producao custa uma madrugada.
#
# ESTE BLOCO E TEMPORARIO. Sai do repositorio quando o ensaio terminar e o
# modulo for aplicado em producao — homologacao volta a ser criada pelo
# infra/subir-homolog.sh, que e sob demanda e mais barato que um ASG parado.
# =============================================================================

module "asg_homolog" {
  source = "./asg"

  ambiente = "homologacao"

  # t3.small, nao t3.medium: homologacao nao precisa da folga que producao tem,
  # e o credito da AWS acaba este mes (ver FABIANO-11). O ensaio nao fica menos
  # valido por rodar numa maquina menor — o que esta sendo testado e o
  # provisionamento, nao a capacidade.
  instance_type = "t3.small"

  # Duas zonas de proposito. Com uma so, queda de zona derruba tudo e o ASG nao
  # tem para onde subir a substituta — o que anula a razao de existir dele.
  subnet_ids = [
    "subnet-0dfc383d51ee3004a", # us-east-1a
    "subnet-0e267e4adcae169a8", # us-east-1b
  ]

  security_group_ids = ["sg-0800a897e3787c63d"] # poc-fabiano-homolog-ec2-sg

  # O Elastic IP que a homolog ja usa. O user_data reassocia no boot com
  # --allow-reassociation: e isso que faz a substituta assumir o mesmo endereco,
  # sem depender de DNS propagar.
  eip_allocation_id = "eipalloc-053acd67132fed0af"

  bucket_imagens   = "cadastro-fabiano-uploads-hml"
  bucket_backup    = "fabiano-db-backups-135133927228"
  bucket_artefatos = "fabiano-artefatos-135133927228"

  # A esteira de develop ja publica em s3://<artefatos>/homologacao/, entao a
  # maquina nova encontra o pacote sem nenhuma mudanca no CI.
  backend_tag = "7ea097a"
  ghcr_owner  = "fabianombs"

  # Sem assinatura de e-mail no ensaio: alarme de homologacao chegando na caixa
  # de quem cuida de producao ensina a ignorar alarme.
  email_alertas = ""

  # Acesso por SSM Session Manager, sem key pair. E como ja entramos nas duas
  # maquinas hoje, e o caminho para fechar a porta 22 (FABIANO-45).
  key_name = "poc-fabiano-homolog-key"
}

output "asg_homolog_nome" {
  description = "Nome do grupo, para acompanhar o ensaio pelo console ou CLI."
  value       = module.asg_homolog.asg_name
}
