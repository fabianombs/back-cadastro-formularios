# =============================================================================
# Cofre externo do backup do banco (FABIANO-20)
# =============================================================================
# Hoje o dump vive em dois lugares: o disco da propria EC2 e o e-mail diario.
# O primeiro morre junto com a maquina; o segundo depende da nossa caixa e do
# limite de 25 MB do Gmail. Nenhum dos dois e uma copia que sobreviva a perda
# do servidor E da nossa empresa ao mesmo tempo.
#
# Este bucket e a terceira copia. O `infra/backup-db.sh` ja sabe escrever nele
# — o bloco existe desde o inicio e esta inerte porque a variavel
# FABIANO_BACKUP_BUCKET nunca foi definida.

resource "aws_s3_bucket" "backup" {
  bucket = "fabiano-db-backups-${data.aws_caller_identity.atual.account_id}"

  tags = {
    Projeto    = var.project_name
    Gerenciado = "terraform"
    Proposito  = "copia externa do dump diario do banco"
  }

  lifecycle {
    prevent_destroy = true
  }
}

# Sem versionamento, um `aws s3 cp` com o nome errado sobrescreve o backup bom
# e nao ha volta. Com versionamento, sobrescrever cria versao nova e a anterior
# continua recuperavel — que e exatamente a propriedade que um backup precisa
# ter e que o disco da maquina nao oferece.
resource "aws_s3_bucket_versioning" "backup" {
  bucket = aws_s3_bucket.backup.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Um dump de producao tem nome, contato e, dependendo do formulario, CPF dos
# participantes cadastrados pelos clientes do Fabiano. Bucket de backup exposto
# e vazamento de base inteira, nao de um registro.
resource "aws_s3_bucket_public_access_block" "backup" {
  bucket = aws_s3_bucket.backup.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  depends_on = [aws_s3_bucket_versioning.backup]

  rule {
    id     = "envelhecer-e-expirar"
    status = "Enabled"

    filter {}

    # Backup de 30 dias atras quase nunca e lido. Standard-IA custa cerca de
    # metade do armazenamento; a cobranca extra por leitura so aparece se
    # alguem restaurar, que e justamente o dia em que o custo nao importa.
    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }

    # A rotacao GFS do backup-db.sh (7 diarios / 4 semanais / 12 mensais) roda
    # no disco da MAQUINA. Aqui a retencao e do lado do bucket, e e mais longa
    # de proposito: se a maquina for comprometida e apagar os proprios
    # arquivos, o que esta aqui continua de pe.
    expiration {
      days = 365
    }

    noncurrent_version_expiration {
      noncurrent_days = 90
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

resource "aws_s3_bucket_policy" "backup" {
  bucket = aws_s3_bucket.backup.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "NegarSemTLS"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource = [
        aws_s3_bucket.backup.arn,
        "${aws_s3_bucket.backup.arn}/*"
      ]
      Condition = {
        Bool = { "aws:SecureTransport" = "false" }
      }
    }]
  })

  depends_on = [aws_s3_bucket_public_access_block.backup]
}

output "bucket_backup" {
  description = "Bucket da copia externa do dump. Vai para FABIANO_BACKUP_BUCKET na linha do cron (FABIANO-20)"
  value       = aws_s3_bucket.backup.bucket
}
