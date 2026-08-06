#!/usr/bin/env bash
# =============================================================================
# Cria o bucket de estado do Terraform — execucao UNICA (FABIANO-10)
# =============================================================================
# NAO E TERRAFORM DE PROPOSITO. O bucket que guarda o state nao pode ser
# gerenciado pelo state que ele mesmo guarda: destruir o bucket exigiria ler o
# state que estava dentro dele. Esse no e conhecido e a saida e sempre a mesma —
# criar o bucket por fora, uma vez, com um script curto e auditavel.
#
# Rodar no AWS CloudShell, com credencial que possa criar bucket.
#
# O STATE GUARDA A SENHA DO BANCO EM TEXTO CLARO. Por isso: bloqueio total de
# acesso publico, versionamento (para recuperar um state corrompido) e cifragem.
# =============================================================================
set -euo pipefail

CONTA="135133927228"
REGIAO="us-east-1"
BUCKET="fabiano-tfstate-${CONTA}"

ATUAL=$(aws sts get-caller-identity --query Account --output text)
[ "$ATUAL" = "$CONTA" ] || { echo "ABORTADO: conta atual e $ATUAL, esperava $CONTA"; exit 1; }

echo ">>> Criando $BUCKET"
# us-east-1 e a unica regiao que NAO aceita LocationConstraint. Passar o
# parametro aqui faz a chamada falhar com InvalidLocationConstraint.
aws s3api create-bucket --bucket "$BUCKET" --region "$REGIAO"

echo ">>> Bloqueando acesso publico"
aws s3api put-public-access-block --bucket "$BUCKET" \
  --public-access-block-configuration \
  "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"

echo ">>> Versionamento (permite voltar um state corrompido)"
aws s3api put-bucket-versioning --bucket "$BUCKET" \
  --versioning-configuration Status=Enabled

echo ">>> Cifragem em repouso"
aws s3api put-bucket-encryption --bucket "$BUCKET" \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"},"BucketKeyEnabled":true}]}'

echo ">>> Exigindo TLS em toda requisicao"
aws s3api put-bucket-policy --bucket "$BUCKET" --policy "{
  \"Version\": \"2012-10-17\",
  \"Statement\": [{
    \"Sid\": \"NegarSemTLS\",
    \"Effect\": \"Deny\",
    \"Principal\": \"*\",
    \"Action\": \"s3:*\",
    \"Resource\": [\"arn:aws:s3:::${BUCKET}\", \"arn:aws:s3:::${BUCKET}/*\"],
    \"Condition\": {\"Bool\": {\"aws:SecureTransport\": \"false\"}}
  }]
}"

echo
echo ">>> Conferencia"
aws s3api get-bucket-versioning --bucket "$BUCKET"
aws s3api get-public-access-block --bucket "$BUCKET" \
  --query 'PublicAccessBlockConfiguration' --output table

echo
echo "Pronto. Proximos passos, nesta ordem:"
echo "  1. descomentar o bloco backend \"s3\" em versions.tf"
echo "  2. terraform init -migrate-state"
echo "  3. terraform plan     <- deve mostrar os 6 imports e NENHUMA outra mudanca"
