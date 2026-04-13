variable "aws_region" {
  description = "Região AWS"
  default     = "us-east-1"
}

variable "project_name" {
  description = "Nome do projeto (usado como prefixo nos recursos)"
  default     = "poc-fabiano"
}

variable "db_name" {
  description = "Nome do banco de dados MySQL"
  default     = "cadastro_fabiano"
}

variable "db_user" {
  description = "Usuário do banco de dados MySQL"
  default     = "admin"
}

variable "db_password" {
  description = "Senha do banco de dados MySQL"
  sensitive   = true
}

variable "ssh_allowed_cidr" {
  description = "CIDR permitido para SSH (restrinja ao seu IP: ex. 177.xxx.xxx.xxx/32)"
  default     = "0.0.0.0/0"
}

variable "public_key_path" {
  description = "Caminho para a chave pública SSH"
  default     = "~/.ssh/poc-fabiano.pub"
}

