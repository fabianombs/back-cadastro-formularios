# =============================================================================
# Bucket de artefatos de deploy (FABIANO-58)
# =============================================================================
# Uma instancia criada pelo Auto Scaling as 3h da manha nao tem endereco
# conhecido nem chave autorizada — a esteira nao tem como fazer scp para ela.
# Ela precisa BUSCAR o pacote de deploy, e este bucket e de onde ela busca.
#
# O `user_data.sh.tftpl` do modulo ./asg ja depende deste caminho:
#   s3://<bucket>/<ambiente>/deploy-bundle.tar.gz
#
# Este e o unico recurso deste repositorio que ainda NAO existe na conta — os
# outros descrevem realidade importada. O primeiro apply o cria.

resource "aws_s3_bucket" "artefatos" {
  bucket = "fabiano-artefatos-${data.aws_caller_identity.atual.account_id}"

  tags = {
    Projeto    = var.project_name
    Gerenciado = "terraform"
    Proposito  = "pacotes de deploy consumidos no boot da instancia"
  }

  # Perder este bucket nao perde dado de cliente, mas deixa qualquer maquina
  # nova sem como subir — e a descoberta seria durante uma recriacao, que e
  # exatamente o pior momento.
  lifecycle {
    prevent_destroy = true
  }
}

# Versionamento e o que permite voltar a um pacote anterior sem depender do Git
# e sem reexecutar a esteira. Tambem protege contra o caso banal: um deploy
# publica um bundle quebrado e sobrescreve o que funcionava.
resource "aws_s3_bucket_versioning" "artefatos" {
  bucket = aws_s3_bucket.artefatos.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "artefatos" {
  bucket = aws_s3_bucket.artefatos.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# O bundle nao tem segredo (o montar-bundle.sh aborta se achar .env), mas tem o
# desenho inteiro da infraestrutura: compose, nginx, scripts de backup. Nada
# disso e util para quem esta de fora, e tudo e util para quem quer atacar.
resource "aws_s3_bucket_public_access_block" "artefatos" {
  bucket = aws_s3_bucket.artefatos.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Com versionamento ligado, cada deploy deixa uma versao antiga para tras. Sem
# expiracao, o bucket cresce para sempre guardando pacotes de 2027 que ninguem
# vai restaurar. 90 dias cobre com folga qualquer rollback plausivel.
resource "aws_s3_bucket_lifecycle_configuration" "artefatos" {
  bucket = aws_s3_bucket.artefatos.id

  # A dependencia e explicita porque a AWS recusa regra de versao nao-atual em
  # bucket sem versionamento, e o Terraform nao infere essa ordem sozinho.
  depends_on = [aws_s3_bucket_versioning.artefatos]

  rule {
    id     = "expirar-pacotes-antigos"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 90
    }

    # Upload interrompido no meio deixa pedacos cobrados e invisiveis no console.
    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

# Exige TLS. Sem isto, um `aws s3 cp` com endpoint http continuaria funcionando
# e o pacote trafegaria em claro — improvavel, mas gratuito de fechar.
resource "aws_s3_bucket_policy" "artefatos" {
  bucket = aws_s3_bucket.artefatos.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "NegarSemTLS"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource = [
        aws_s3_bucket.artefatos.arn,
        "${aws_s3_bucket.artefatos.arn}/*"
      ]
      Condition = {
        Bool = { "aws:SecureTransport" = "false" }
      }
    }]
  })

  depends_on = [aws_s3_bucket_public_access_block.artefatos]
}

output "bucket_artefatos" {
  description = "Bucket de onde a instancia nova baixa o pacote de deploy (FABIANO-58)"
  value       = aws_s3_bucket.artefatos.bucket
}
