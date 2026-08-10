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

# =============================================================================
# A esteira cria a homolog sob demanda (FABIANO-33, etapa 6b)
# =============================================================================
# DESENHO, decidido em 09/08/2026:
#
#   push na develop -> a esteira pergunta se existe EC2 com Ambiente=homolog
#                      nao existe? cria. existe? segue.
#                   -> deploy
#                   -> 24h sem uso -> a maquina se TERMINA sozinha
#
# Quem apaga a MAQUINA e ela mesma, de dentro, com
# InstanceInitiatedShutdownBehavior=terminate — e para isso nao existe permissao
# a conceder. Por isso nao ha ec2:TerminateInstances aqui.
#
# CORRECAO DE 10/08/2026: eu tinha escrito neste ponto que a politica nao
# precisaria de NENHUMA acao destrutiva. Era falso, e o jeito de descobrir seria
# esperar 24h. A maquina se mata; o banco dela, nao. Sobra um RDS cobrando e o
# ciclo seguinte quebra. O rds:DeleteDBInstance no fim do arquivo existe por
# isso, preso a um unico nome literal.
#
# O que sobra de perigoso e o ec2:RunInstances, que sem condicao permitiria
# subir qualquer coisa de qualquer tamanho. As condicoes abaixo o prendem a:
# uma tag obrigatoria, um tipo de instancia, uma subnet, um SG e uma chave.
# =============================================================================

data "aws_caller_identity" "esta_conta" {}

locals {
  conta  = data.aws_caller_identity.esta_conta.account_id
  regiao = "us-east-1"

  # Recursos que a homolog usa. Estao aqui, e nao espalhados pela politica,
  # para o dia em que mudarem ser um dia de editar um lugar so.
  hml_sg_ec2   = "sg-0800a897e3787c63d"
  hml_subnet   = "subnet-0dfc383d51ee3004a"
  hml_chave    = "poc-fabiano-homolog-key"
  hml_eip      = "eipalloc-053acd67132fed0af"
  hml_tipo     = "t3.small"
  hml_banco    = "poc-fabiano-homolog-db"
  prod_ec2     = "i-008f8d272588845ef"
  prod_banco   = "poc-fabiano-db"
}

resource "aws_iam_role_policy" "github_homolog" {
  name = "subir-homolog"
  role = aws_iam_role.github_deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # Describe* nao aceita escopo por recurso — sao operacoes de listagem.
        # E leitura, e o script precisa delas para descobrir se homolog ja
        # existe antes de criar uma segunda.
        Sid    = "LerEstado"
        Effect = "Allow"
        Action = [
          "ec2:DescribeInstances", "ec2:DescribeImages", "ec2:DescribeAddresses",
          "ec2:DescribeSubnets", "ec2:DescribeSecurityGroups", "ec2:DescribeKeyPairs",
          "ec2:DescribeSnapshots", "ec2:DescribeTags",
          "rds:DescribeDBInstances", "rds:DescribeDBSnapshots"
        ]
        Resource = "*"
      },
      {
        # A imagem sai da instancia de PRODUCAO, e so dela. O ARN fixo aqui e o
        # que impede a esteira de clonar qualquer outra maquina da conta.
        Sid    = "ImagemDaProducao"
        Effect = "Allow"
        Action = "ec2:CreateImage"
        Resource = [
          "arn:aws:ec2:${local.regiao}:${local.conta}:instance/${local.prod_ec2}",
          "arn:aws:ec2:${local.regiao}::image/*",
          # Snapshot de EBS leva o id da conta no ARN; imagem, nao. As duas
          # formas ficam aqui porque errar isso so aparece no meio de um
          # create-image, com a AMI ja criada e o job morrendo em seguida.
          "arn:aws:ec2:${local.regiao}::snapshot/*",
          "arn:aws:ec2:${local.regiao}:${local.conta}:snapshot/*"
        ]
      },
      {
        # A acao mais perigosa do arquivo, presa por duas condicoes: a instancia
        # PRECISA nascer com Ambiente=homolog, e PRECISA ser t3.small. Sem a
        # primeira, o auto-desligamento nunca acharia a maquina (ele busca por
        # essa tag) e ela viveria para sempre. Sem a segunda, um erro de
        # digitacao poderia subir uma maquina de centenas de dolares por dia.
        Sid      = "SubirInstanciaDeHomolog"
        Effect   = "Allow"
        Action   = "ec2:RunInstances"
        Resource = "arn:aws:ec2:${local.regiao}:${local.conta}:instance/*"
        Condition = {
          StringEquals = {
            "aws:RequestTag/Ambiente" = "homolog"
            "ec2:InstanceType"        = local.hml_tipo
          }
        }
      },
      {
        # O volume precisa de um bloco SEPARADO, e a razao e sutil o bastante
        # para ter custado um ciclo inteiro da esteira em 10/08/2026.
        #
        # Um RunInstances cria varios recursos numa chamada so, e a AWS avalia a
        # permissao de cada um contra as chaves de contexto QUE EXISTEM PARA
        # AQUELE RECURSO. 'ec2:InstanceType' existe para 'instance'; para
        # 'volume', nao existe. E um StringEquals sobre chave ausente nunca casa
        # — entao a condicao que protege a instancia, aplicada ao volume,
        # tornava a chamada impossivel de autorizar.
        #
        # A tag continua exigida aqui (o run-instances etiqueta o volume junto).
        # Perder o limite de tipo neste bloco nao afrouxa nada: os dois blocos
        # precisam passar para a chamada existir, e o de cima ja barra o tipo.
        Sid      = "DiscoDaInstanciaDeHomolog"
        Effect   = "Allow"
        Action   = "ec2:RunInstances"
        Resource = "arn:aws:ec2:${local.regiao}:${local.conta}:volume/*"
        Condition = {
          StringEquals = {
            "aws:RequestTag/Ambiente" = "homolog"
          }
        }
      },
      {
        # A mesma chamada RunInstances precisa de permissao sobre a rede, o SG,
        # a chave e a imagem. Como condicao de tag nao se aplica a estes, o
        # escopo vem dos ARNs: uma subnet, um SG, uma chave. Nao da para subir
        # numa rede diferente nem com a chave de producao.
        Sid      = "RedeEChaveDeHomolog"
        Effect   = "Allow"
        Action   = "ec2:RunInstances"
        Resource = [
          "arn:aws:ec2:${local.regiao}:${local.conta}:subnet/${local.hml_subnet}",
          "arn:aws:ec2:${local.regiao}:${local.conta}:security-group/${local.hml_sg_ec2}",
          "arn:aws:ec2:${local.regiao}:${local.conta}:network-interface/*",
          "arn:aws:ec2:${local.regiao}:${local.conta}:key-pair/${local.hml_chave}",
          "arn:aws:ec2:${local.regiao}::image/*"
        ]
      },
      {
        # Etiquetar so no momento da criacao. Sem a condicao, a esteira poderia
        # reetiquetar qualquer recurso da conta — inclusive tirar o Ambiente de
        # producao ou marcar producao como homolog, que faria o auto-desligamento
        # apontar a arma para o lado errado.
        Sid      = "EtiquetarSomenteAoCriar"
        Effect   = "Allow"
        Action   = "ec2:CreateTags"
        Resource = "*"
        Condition = {
          StringEquals = {
            "ec2:CreateAction" = ["RunInstances", "CreateImage"]
          }
        }
      },
      {
        # Um unico Elastic IP, o de homolog. O de producao nao esta aqui.
        Sid      = "AssociarOEipDeHomolog"
        Effect   = "Allow"
        Action   = "ec2:AssociateAddress"
        Resource = [
          "arn:aws:ec2:${local.regiao}:${local.conta}:elastic-ip/${local.hml_eip}",
          "arn:aws:ec2:${local.regiao}:${local.conta}:instance/*"
        ]
      },
      {
        # Snapshot do banco de producao. Leitura, nao destrutiva: criar snapshot
        # nao afeta a instancia de origem.
        Sid      = "SnapshotDaProducao"
        Effect   = "Allow"
        Action   = "rds:CreateDBSnapshot"
        Resource = [
          "arn:aws:rds:${local.regiao}:${local.conta}:db:${local.prod_banco}",
          "arn:aws:rds:${local.regiao}:${local.conta}:snapshot:homolog-base-*"
        ]
      },
      {
        # Restaurar SO com o identificador de homolog. Com o ARN fixo, nao ha
        # como a esteira restaurar por cima de producao nem criar um banco de
        # nome livre que ninguem depois saberia explicar na fatura.
        Sid      = "RestaurarComoHomolog"
        Effect   = "Allow"
        Action   = "rds:RestoreDBInstanceFromDBSnapshot"
        Resource = [
          "arn:aws:rds:${local.regiao}:${local.conta}:db:${local.hml_banco}",
          "arn:aws:rds:${local.regiao}:${local.conta}:snapshot:homolog-base-*",
          "arn:aws:rds:${local.regiao}:${local.conta}:subgrp:*",
          "arn:aws:rds:${local.regiao}:${local.conta}:pg:*"
        ]
      },
      {
        Sid      = "EtiquetarRecursosDeHomolog"
        Effect   = "Allow"
        Action   = "rds:AddTagsToResource"
        Resource = [
          "arn:aws:rds:${local.regiao}:${local.conta}:db:${local.hml_banco}",
          "arn:aws:rds:${local.regiao}:${local.conta}:snapshot:homolog-base-*"
        ]
      },
      {
        # ---------------------------------------------------------------------
        # AQUI ENTRA A UNICA ACAO DESTRUTIVA DESTE ARQUIVO, e ela merece
        # explicacao porque contradiz o comentario la de cima.
        # ---------------------------------------------------------------------
        # O desenho original dizia "esta politica nao precisa de nada
        # destrutivo, quem apaga e a propria maquina". Estava errado pela
        # metade: a maquina se apaga, mas o BANCO dela nao. O auto-desligamento
        # nao tem papel IAM — de proposito — entao o banco de homolog sobrevive
        # a morte da EC2, cobrando ~US$ 15/mes para sempre, e faz o ciclo
        # seguinte quebrar no restore com DBInstanceAlreadyExists.
        #
        # A regra "sem acao destrutiva" era uma heuristica para impedir que a
        # esteira pudesse machucar producao. Um DeleteDBInstance preso a UM
        # nome literal nao consegue nomear producao nem por erro de digitacao:
        # nao existe curinga aqui. A heuristica continua respeitada; o que muda
        # e o meio.
        Sid      = "ApagarBancoDeHomolog"
        Effect   = "Allow"
        Action   = "rds:DeleteDBInstance"
        Resource = "arn:aws:rds:${local.regiao}:${local.conta}:db:${local.hml_banco}"
      },
      {
        # Os snapshots de base acumulam um por ciclo. Sem apaga-los, o custo
        # cresce em degraus que ninguem associa a homolog quando a fatura chega.
        Sid      = "ApagarSnapshotDeHomolog"
        Effect   = "Allow"
        Action   = "rds:DeleteDBSnapshot"
        Resource = "arn:aws:rds:${local.regiao}:${local.conta}:snapshot:homolog-base-*"
      },
      {
        # Escopo por TAG, nao por ARN, porque o id da AMI muda a cada ciclo.
        # A trava e que a esteira so consegue POR a tag Ambiente=homolog no
        # momento da criacao (ver EtiquetarSomenteAoCriar) — ela nao tem como
        # etiquetar um recurso de producao como homolog para depois apaga-lo.
        Sid    = "ApagarImagemEDiscoDeHomolog"
        Effect = "Allow"
        Action = ["ec2:DeregisterImage", "ec2:DeleteSnapshot"]
        Resource = [
          "arn:aws:ec2:${local.regiao}::image/*",
          "arn:aws:ec2:${local.regiao}::snapshot/*",
          "arn:aws:ec2:${local.regiao}:${local.conta}:snapshot/*"
        ]
        Condition = {
          StringEquals = {
            "ec2:ResourceTag/Ambiente" = "homolog"
          }
        }
      }
    ]
  })
}

output "role_github_deploy" {
  description = "ARN para gravar no segredo AWS_ROLE_DEPLOY do repositorio (FABIANO-58)"
  value       = aws_iam_role.github_deploy.arn
}
