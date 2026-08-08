# =============================================================================
# GitHub Actions publica o pacote de deploy no S3 (FABIANO-58)
# =============================================================================
# Sem isto, a unica forma de a esteira escrever na conta seria uma chave de
# acesso guardada como segredo do repositorio: credencial permanente, que nao
# expira, que vaza inteira se alguem imprimir o ambiente num log.
#
# OIDC troca isso por um token de minutos, emitido pelo proprio GitHub para uma
# execucao especifica, e que a AWS so aceita se vier do repositorio e da branch
# declarados abaixo.

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]

  # A AWS valida o certificado do GitHub pelas CAs publicas desde 2023 e ignora
  # este campo para provedores conhecidos. A API continua exigindo o valor, e
  # este e o thumbprint historico do GitHub.
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]

  tags = {
    Projeto    = var.project_name
    Gerenciado = "terraform"
  }
}

resource "aws_iam_role" "github_deploy" {
  name        = "fabiano-github-deploy"
  description = "Assumida pelo GitHub Actions para publicar o pacote de deploy no S3"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        }
        # A condicao mais importante do arquivo. Sem ela, QUALQUER repositorio
        # do GitHub — de qualquer pessoa no mundo — poderia assumir esta role.
        # O 'sub' amarra ao repositorio E a branch: um pull request de fora,
        # que roda com ref de PR e nao de branch, nao passa.
        StringLike = {
          "token.actions.githubusercontent.com:sub" = [
            "repo:fabianombs/back-cadastro-formularios:ref:refs/heads/master",
            "repo:fabianombs/back-cadastro-formularios:ref:refs/heads/develop",
          ]
        }
      }
    }]
  })

  tags = {
    Projeto    = var.project_name
    Gerenciado = "terraform"
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name = "publicar-pacote"
  role = aws_iam_role.github_deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # Escreve o pacote e nada mais. Sem s3:DeleteObject: a esteira publica
        # versao nova, nunca apaga a anterior — quem expira e a regra de ciclo
        # de vida do bucket, que nao depende de quem esta rodando o deploy.
        Sid      = "PublicarPacote"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:AbortMultipartUpload"]
        Resource = "${aws_s3_bucket.artefatos.arn}/*"
      },
      {
        Sid      = "ConferirBucket"
        Effect   = "Allow"
        Action   = ["s3:ListBucket", "s3:GetBucketLocation"]
        Resource = aws_s3_bucket.artefatos.arn
      }
    ]
  })
}

output "role_github_deploy" {
  description = "ARN para gravar no segredo AWS_ROLE_DEPLOY do repositorio (FABIANO-58)"
  value       = aws_iam_role.github_deploy.arn
}
