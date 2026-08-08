# =============================================================================
# Variaveis do ambiente de PRODUCAO (FABIANO-10)
# =============================================================================
# Os defaults descrevem a realidade da conta 135133927228, coletada em
# 04/08/2026 e conferida em 06/08/2026. Mudar um default aqui sem conferir a
# AWS faz o `terraform plan` propor alteracao em producao.

variable "aws_region" {
  description = "Regiao AWS"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Prefixo dos recursos. Faz parte do identificador do RDS e do nome dos SGs — mudar recria tudo."
  type        = string
  default     = "poc-fabiano"
}

variable "ambiente" {
  description = "Rotulo do ambiente. Hoje so 'producao' e gerenciado por este state."
  type        = string
  default     = "producao"
}

# ---- EC2 --------------------------------------------------------------------

variable "instance_type" {
  description = "Tipo da instancia de aplicacao. Real hoje: t2.micro."
  type        = string
  default     = "t2.micro"
}

variable "ami_id" {
  description = <<-TXT
    AMI da instancia EXISTENTE. Amazon Linux 2, apesar do comentario original
    dizer AL2023. Fixada de proposito: `ami` e ForceNew, entao qualquer mudanca
    aqui propoe destruir e recriar a EC2 de producao. A resolucao dinamica vive
    no data.aws_ami.al2023, usada pelo modulo ./asg.
  TXT
  type        = string
  default     = "ami-0c02fb55956c7d316"
}

variable "public_key_material" {
  description = <<-TXT
    Conteudo da chave publica SSH (ed25519) do key pair poc-fabiano-key.

    Depois do import este campo e inerte: a AWS nao permite trocar a chave de um
    key pair — alterar recria o recurso, e recriar invalidaria o acesso a
    maquina. Por isso o recurso tem ignore_changes = [public_key].

    Fica como variavel, e nao `file("~/.ssh/poc-fabiano.pub")` como antes, porque
    o runner do CI nao tem esse arquivo e o `file()` falha no plan — quebrando o
    detector de drift justamente onde ele deveria rodar.
  TXT
  type        = string
  default     = ""
}

variable "ssh_allowed_cidr" {
  description = "CIDR liberado para SSH. Hoje 0.0.0.0/0 na AWS — restringir depende do FABIANO-45."
  type        = string
  default     = "0.0.0.0/0"
}

# ---- RDS --------------------------------------------------------------------

variable "db_name" {
  description = "DBName com que o RDS foi criado. NAO e o banco que a aplicacao usa (poc_fabiano_new, via DB_NAME)."
  type        = string
  default     = "cadastro_fabiano"
}

variable "db_user" {
  description = "Usuario master do RDS"
  type        = string
  default     = "admin"
}

variable "db_password" {
  description = <<-TXT
    Senha do usuario master. O import NAO consegue ler este valor da AWS — a API
    nao devolve senha — entao o state fica com o campo vazio e o plan propoe
    troca-la. Por isso o recurso tem ignore_changes = [password].

    Deixar vazio aqui e o comportamento esperado depois do import. O caminho
    definitivo e migrar para manage_master_user_password (Secrets Manager), que
    tira a senha do state — mas isso e uma alteracao no RDS de producao e merece
    card proprio.
  TXT
  type        = string
  sensitive   = true
  default     = ""
}

variable "db_engine_version" {
  description = "Versao real hoje. O upgrade para 8.4 e o FABIANO-9 — quando acontecer, atualizar aqui DEPOIS, para o plan confirmar."
  type        = string
  default     = "8.0.45"
}

variable "db_parameter_group" {
  description = "Parameter group em uso. Existe tambem um 'poc-fabiano-mysql84' criado pela CLI, ainda nao usado (FABIANO-6)."
  type        = string
  default     = "default.mysql8.0"
}

variable "db_backup_retention" {
  description = "Dias de retencao do backup automatico. REALIDADE: 7. O .tf antigo dizia 0 — aplicar aquilo teria DESLIGADO o backup de producao."
  type        = number
  default     = 7
}

variable "db_multi_az" {
  description = "Multi-AZ. Hoje false. Ligar dobra o custo do RDS e e a unica protecao contra queda de zona inteira."
  type        = bool
  default     = false
}
