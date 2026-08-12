# =============================================================================
# Identidade da instancia — o que destrava o resto (FABIANO-20, FABIANO-10)
# =============================================================================
# Hoje a EC2 nao tem credencial AWS nenhuma. E por isso que o backup offsite nao
# existe, que os segredos moram num arquivo no disco, e que a maquina nao pode
# se recriar sozinha. Uma role resolve os tres.
#
# ATENCAO: criar role exige permissao de IAM, que o usuario contato@ nao tem.
# Enquanto isso nao for liberado, este modulo nao pode ser aplicado. Ver o script
# criar-role-fabiano.sh, preparado para quem tiver acesso de administrador.

resource "aws_iam_role" "instancia" {
  name        = "${var.project_name}-${var.ambiente}-ec2${var.sufixo_iam}"
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

  tags = { Projeto = var.project_name, Ambiente = var.ambiente }
}

# Session Manager: shell na maquina sem porta 22 aberta e sem chave privada
# circulando. E o caminho para um dia fechar o SSH do security group (FABIANO-45)
# sem perder acesso.
resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.instancia.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "operacao" {
  name = "operacao"
  role = aws_iam_role.instancia.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # ESCREVER backup. Sem s3:DeleteObject de proposito: a maquina nunca
        # apaga. Se ela for invadida, o historico de backup continua intacto —
        # e destruir backup e a primeira coisa que ransomware faz.
        Sid      = "EscreverBackup"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:AbortMultipartUpload"]
        Resource = "arn:aws:s3:::${var.bucket_backup}/*"
      },
      {
        Sid      = "ListarBackup"
        Effect   = "Allow"
        Action   = ["s3:ListBucket", "s3:GetBucketLocation"]
        Resource = "arn:aws:s3:::${var.bucket_backup}"
      },
      {
        # LER o pacote de deploy no boot. Somente leitura.
        Sid      = "LerArtefatoDeDeploy"
        Effect   = "Allow"
        Action   = ["s3:GetObject"]
        Resource = "arn:aws:s3:::${var.bucket_artefatos}/${var.ambiente}/*"
      },
      # Imagens da aplicacao. Sem isto o upload falha com 403 do S3 e TODO o
      # resto funciona — sintoma que se confunde facilmente com bug do app.
      # Sem s3:DeleteBucket nem politica de bucket: a maquina escreve e le
      # objetos, nunca administra o bucket.
      {
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
        Resource = "arn:aws:s3:::${var.bucket_imagens}/*"
      },
      {
        Effect   = "Allow"
        Action   = ["s3:ListBucket", "s3:GetBucketLocation"]
        Resource = "arn:aws:s3:::${var.bucket_imagens}"
      },
      {
        # LER os proprios segredos. Escopo fechado no ambiente: a maquina de
        # homologacao nao alcanca os parametros de producao.
        Sid    = "LerSegredosDoProprioAmbiente"
        Effect = "Allow"
        Action = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
        Resource = "arn:aws:ssm:${data.aws_region.atual.name}:${data.aws_caller_identity.atual.account_id}:parameter/${var.project_name}/${var.ambiente}/*"
      },
      {
        # Decifrar SecureString — e so isso. A condicao amarra a chave ao uso
        # via Parameter Store; nao serve para decifrar mais nada na conta.
        Sid      = "DecifrarSomenteViaParameterStore"
        Effect   = "Allow"
        Action   = "kms:Decrypt"
        Resource = "*"
        Condition = {
          StringEquals = {
            "kms:ViaService" = "ssm.${data.aws_region.atual.name}.amazonaws.com"
          }
        }
      },
      {
        # Associar o Elastic IP a si mesma no boot. E o que permite ao ASG
        # trocar de instancia sem trocar o endereco — e sem mexer em DNS.
        Sid      = "AssumirOEnderecoFixo"
        Effect   = "Allow"
        Action   = ["ec2:AssociateAddress", "ec2:DescribeAddresses"]
        Resource = "*"
      },
      {
        # Publicar metrica propria (espaco de disco, por exemplo). O
        # node-exporter cobre o painel; isto cobre o alarme, que precisa viver
        # FORA da maquina para servir quando ela morrer.
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
  name = "${var.project_name}-${var.ambiente}-ec2${var.sufixo_iam}"
  role = aws_iam_role.instancia.name
}
