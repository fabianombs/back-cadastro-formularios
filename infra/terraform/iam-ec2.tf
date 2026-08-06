# =============================================================================
# Identidade da EC2 de producao (FABIANO-20, FABIANO-58)
# =============================================================================
# Ate hoje a maquina nao tinha credencial nenhuma da AWS. E por isso que o
# backup offsite nao existia, que os segredos moram num arquivo no disco, e que
# ela nao consegue se recriar sozinha. Uma role resolve os tres.
#
# O `iam:CreateRole` deu AccessDenied ate 06/08/2026, quando o dono da conta
# anexou AdministratorAccess ao usuario contato@resultatec.com.br.
#
# > ATENCAO — duplicacao a resolver
# > O modulo ./asg tambem declara uma role equivalente (asg/iam.tf). Ele e
# > inerte hoje: nada o referencia. Quando o FABIANO-57 for ligado, aquele
# > arquivo precisa passar a CONSUMIR esta role por variavel, em vez de criar
# > outra — senao a conta fica com duas identidades fazendo a mesma coisa e o
# > plan proporia criar a segunda.

resource "aws_iam_role" "instancia" {
  name        = "${var.project_name}-${var.ambiente}-ec2"
  description = "Identidade da EC2 do projeto Fabiano (${var.ambiente})"

  # Somente o servico EC2 assume. Nenhuma pessoa, nenhuma outra conta.
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })

  tags = {
    Projeto    = var.project_name
    Ambiente   = var.ambiente
    Gerenciado = "terraform"
  }
}

# Session Manager: shell na maquina sem porta 22 aberta e sem chave privada
# circulando. E o caminho para um dia fechar o SSH do security group
# (FABIANO-45) sem perder acesso — e a rede de seguranca para o dia em que a
# chave .pem se perder.
resource "aws_iam_role_policy_attachment" "instancia_ssm" {
  role       = aws_iam_role.instancia.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "instancia" {
  name = "operacao"
  role = aws_iam_role.instancia.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # ESCREVER backup. Sem s3:DeleteObject, de proposito: a maquina nunca
        # apaga. Se ela for invadida, o historico continua intacto — e destruir
        # backup e a primeira coisa que ransomware faz. Quem expira objeto e a
        # regra de ciclo de vida do bucket, que roda do lado da AWS e nao
        # depende de nada que esteja rodando no servidor.
        Sid      = "EscreverBackup"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:AbortMultipartUpload"]
        Resource = "${aws_s3_bucket.backup.arn}/*"
      },
      {
        # O backup-db.sh confere o objeto depois de enviar.
        Sid      = "ConferirBackup"
        Effect   = "Allow"
        Action   = ["s3:ListBucket", "s3:GetBucketLocation"]
        Resource = aws_s3_bucket.backup.arn
      },
      {
        # LER o pacote de deploy no boot (FABIANO-58). Somente leitura, e
        # somente o prefixo do proprio ambiente.
        Sid      = "LerPacoteDeDeploy"
        Effect   = "Allow"
        Action   = ["s3:GetObject"]
        Resource = "${aws_s3_bucket.artefatos.arn}/${var.ambiente}/*"
      },
      {
        # LER os proprios segredos do Parameter Store. Escopo fechado no
        # ambiente: a maquina de homologacao nao alcanca os parametros de
        # producao, nem o contrario.
        Sid      = "LerSegredosDoProprioAmbiente"
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
        Resource = "arn:aws:ssm:${var.aws_region}:${data.aws_caller_identity.atual.account_id}:parameter/${var.project_name}/${var.ambiente}/*"
      },
      {
        # Decifrar SecureString — e so isso. A condicao amarra ao uso via
        # Parameter Store; nao serve para decifrar mais nada na conta.
        Sid      = "DecifrarSomenteViaParameterStore"
        Effect   = "Allow"
        Action   = "kms:Decrypt"
        Resource = "*"
        Condition = {
          StringEquals = {
            "kms:ViaService" = "ssm.${var.aws_region}.amazonaws.com"
          }
        }
      },
      {
        # Publicar metrica propria. O alarme que importa precisa viver FORA da
        # maquina, para servir no dia em que ela morrer.
        Sid      = "PublicarMetricaNoCloudWatch"
        Effect   = "Allow"
        Action   = ["cloudwatch:PutMetricData"]
        Resource = "*"
        Condition = {
          StringEquals = { "cloudwatch:namespace" = "Fabiano/${var.ambiente}" }
        }
      }
    ]
  })
}

resource "aws_iam_instance_profile" "instancia" {
  name = "${var.project_name}-${var.ambiente}-ec2"
  role = aws_iam_role.instancia.name

  tags = {
    Projeto    = var.project_name
    Gerenciado = "terraform"
  }
}

output "role_instancia" {
  description = "Role da EC2 de producao (FABIANO-20)"
  value       = aws_iam_role.instancia.name
}
