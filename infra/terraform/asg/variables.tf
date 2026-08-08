# =============================================================================
# Modulo ASG — variaveis
# =============================================================================
# Os defaults descrevem a maquina que JA foi validada em homologacao
# (i-008f8d272588845ef): t3.medium, AL2023, 20 GB. Nao sao chute.

variable "ambiente" {
  description = "producao | homologacao. Entra no nome dos recursos e no caminho dos parametros no SSM."
  type        = string
  validation {
    condition     = contains(["producao", "homologacao"], var.ambiente)
    error_message = "ambiente deve ser 'producao' ou 'homologacao'."
  }
}

variable "project_name" {
  type    = string
  default = "poc-fabiano"
}

variable "instance_type" {
  description = "Validado em homologacao em 06/08/2026: t3.medium (4 GB) cabe a aplicacao (teto 700m) mais a stack de observabilidade com folga."
  type        = string
  default     = "t3.medium"
}

variable "volume_size" {
  description = "Disco raiz em GB. A maquina validada usa 20."
  type        = number
  default     = 20
}

variable "cpu_credits" {
  description = <<-TXT
    'unlimited' ou 'standard' para instancias burstaveis.

    'unlimited' evita que a CPU seja estrangulada quando os creditos acabam —
    o cenario e exatamente o evento cheio do Fabiano, com o tablet marcando
    presenca. O custo extra so aparece se a media passar da linha de base, e e
    de centavos.

    'standard' e mais barato no pior caso e mais lento no pior caso. Para um
    servidor que existe para aguentar pico, e a escolha errada.
  TXT
  type        = string
  default     = "unlimited"
}

variable "security_group_ids" {
  description = "SGs da instancia. Hoje as duas maquinas compartilham o poc-fabiano-ec2-sg (sg-0a32a93ab12d715f0)."
  type        = list(string)
}

variable "subnet_ids" {
  description = "Sub-redes do ASG. Use pelo menos DUAS, em zonas diferentes — com uma so, queda de zona derruba tudo e o ASG nao tem para onde subir a substituta."
  type        = list(string)
}

variable "key_name" {
  description = "Key pair SSH. Pode ficar vazio se o acesso for so por SSM Session Manager, que e o desejavel."
  type        = string
  default     = ""
}

variable "eip_allocation_id" {
  description = <<-TXT
    Elastic IP que a instancia associa a si mesma no boot.

    E o que faz o ASG conviver com DNS estavel: o endereco pertence a conta, nao
    a instancia, entao a substituta assume o mesmo IP e nenhum registro DNS
    precisa mudar.
  TXT
  type        = string
}

variable "bucket_backup" {
  description = "Bucket dos dumps. Criado em 05/08: fabiano-db-backups-135133927228."
  type        = string
}

variable "bucket_artefatos" {
  description = <<-TXT
    Bucket de onde a instancia baixa o pacote de deploy no boot.

    PRE-REQUISITO AINDA NAO ATENDIDO: hoje os arquivos de deploy/ chegam na
    maquina por scp, disparado pelo CI. Uma instancia nova nasceria sem eles.
    A esteira precisa passar a publicar deploy-bundle.tar.gz neste bucket.
    Ver o card aberto para isso.
  TXT
  type        = string
}

variable "backend_tag" {
  description = "Tag da imagem no GHCR que a instancia sobe no boot. Vem do mesmo valor que o .env usa hoje."
  type        = string
}

variable "ghcr_owner" {
  type = string
}

variable "email_alertas" {
  description = "Destinatario dos alarmes do CloudWatch. Vazio nao cria assinatura — e o alarme fica so no console."
  type        = string
  default     = ""
}

variable "usar_alb" {
  description = <<-TXT
    Coloca um Application Load Balancer na frente e troca a saude do ASG de EC2
    para ELB — a diferenca entre "a maquina esta de pe" e "a aplicacao responde".

    DESLIGADO por padrao, e nao por economia (uns US$ 18/mes): com ALB faz
    sentido ter mais de uma instancia, e mais de uma instancia hoje NAO funciona
    — os anexos anteriores a migracao para o S3 moram no disco de uma maquina so
    (FABIANO-19). Uma segunda instancia serviria link quebrado para eles.
  TXT
  type        = bool
  default     = false
}
