data "aws_caller_identity" "atual" {}
data "aws_region" "atual" {}

# AMI resolvida, e nao fixada. Aqui isto e seguro — diferente da instancia solta
# do modulo raiz, onde trocar a AMI recriaria a maquina de producao. Num ASG,
# nascer com AMI mais nova e o comportamento desejado; a substituicao acontece
# quando a instancia morre, nao no `apply`.
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-kernel-6.1-x86_64"]
  }
  filter {
    name   = "state"
    values = ["available"]
  }
}
