# =============================================================================
# Fontes de dados — o que ja existe na conta e nao e gerenciado aqui
# =============================================================================

data "aws_caller_identity" "atual" {}

# A VPC default e onde tudo foi criado a mao em 2026. Declarar explicitamente
# evita que um dia alguem rode isto numa conta com outra VPC default e crie
# recurso no lugar errado sem perceber.
data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# -----------------------------------------------------------------------------
# AMI do Amazon Linux 2023, resolvida em vez de fixada
# -----------------------------------------------------------------------------
# O criterio de aceite do FABIANO-10 pede AMI por data source. Ela NAO e usada
# na instancia importada, e o motivo importa:
#
#   A instancia de producao roda `ami-0c02fb55956c7d316`, que e Amazon Linux 2
#   (o comentario original no main.tf dizia AL2023 e estava errado). Trocar o
#   valor de `ami` e ForceNew: o plan proporia DESTRUIR E RECRIAR a EC2 de
#   producao. Por isso a instancia mantem o id fixo com ignore_changes.
#
# Este data source alimenta o LAUNCH TEMPLATE do Auto Scaling (modulo ./asg),
# que e onde a AMI passa a ser resolvida de verdade. Quando o ASG substituir a
# instancia solta, o id fixo desaparece do repositorio junto com ela.
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
