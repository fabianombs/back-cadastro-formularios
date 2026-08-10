#!/usr/bin/env bash
# =============================================================================
# Derruba o ambiente de homologacao e zera o custo (FABIANO-33)
# =============================================================================
# ONDE RODAR: CloudShell da AWS.
#
# TERMINAR, NAO PARAR. RDS parado a AWS RELIGA SOZINHA depois de 7 dias, e voce
# descobre pela fatura. E como o subir-homolog.sh recria tudo em ~15 minutos a
# partir da producao daquele momento, nao ha nada aqui que valha preservar —
# preservar, alias, seria o contrario do objetivo: homolog guardado deriva.
#
# O Elastic IP fica. Ele custa ~US$ 3,60/mes parado, ja esta pago, e carrega o
# DNS de api-hml e grafana-hml. Liberar significaria perder o endereco para a
# AWS e refazer DNS e certificado no proximo ciclo.
# =============================================================================
set -euo pipefail

REGIAO=us-east-1
HML_BANCO=poc-fabiano-homolog-db

msg() { echo -e "\n=== $* ==="; }

# -----------------------------------------------------------------------------
# 1. A EC2
# -----------------------------------------------------------------------------
# Busca por TAG e nao por id fixo: o id muda a cada ciclo, e script com id
# gravado a mao e script que um dia apaga a instancia errada.
msg "1. procurando a EC2 de homolog"
INSTANCIA=$(aws ec2 describe-instances --region "$REGIAO" \
  --filters "Name=tag:Ambiente,Values=homolog" \
            "Name=instance-state-name,Values=pending,running,stopping,stopped" \
  --query 'Reservations[].Instances[].InstanceId' --output text)

if [ -z "$INSTANCIA" ]; then
  echo "nenhuma EC2 de homolog no ar"
else
  # Trava de seguranca: se a busca por tag devolver algo que NAO esteja marcado
  # como homolog, para tudo. Custa uma consulta e evita a manchete.
  for i in $INSTANCIA; do
    AMB=$(aws ec2 describe-instances --region "$REGIAO" --instance-ids "$i" \
      --query 'Reservations[].Instances[].Tags[?Key==`Ambiente`].Value|[0][0]' --output text)
    if [ "$AMB" != "homolog" ]; then
      echo "ABORTANDO: ${i} nao esta marcada como homolog (Ambiente=${AMB})"
      exit 1
    fi
  done
  echo "terminando: $INSTANCIA"
  aws ec2 terminate-instances --region "$REGIAO" --instance-ids $INSTANCIA \
    --query 'TerminatingInstances[].[InstanceId,CurrentState.Name]' --output text
fi

# -----------------------------------------------------------------------------
# 2. O banco
# -----------------------------------------------------------------------------
msg "2. apagando o banco de homolog"
if aws rds describe-db-instances --region "$REGIAO" \
     --db-instance-identifier "$HML_BANCO" >/dev/null 2>&1; then
  # --skip-final-snapshot aqui e correto e nao descuidado: o conteudo veio de um
  # snapshot da producao e nao tem nada que nao exista la. Snapshot final seria
  # pagar armazenamento por uma copia de teste.
  aws rds delete-db-instance --region "$REGIAO" \
    --db-instance-identifier "$HML_BANCO" \
    --skip-final-snapshot --delete-automated-backups \
    --query 'DBInstance.[DBInstanceIdentifier,DBInstanceStatus]' --output text
else
  echo "nenhum banco de homolog encontrado"
fi

# -----------------------------------------------------------------------------
# 3. Limpeza das bases antigas
# -----------------------------------------------------------------------------
# AMI e snapshot de ciclos passados nao servem para nada: o proximo ciclo tira
# imagem nova da producao daquele dia — que e o ponto do desenho. Guardar as
# antigas so acumula armazenamento cobrado em silencio.
msg "3. limpando AMIs e snapshots de ciclos anteriores"

for AMI in $(aws ec2 describe-images --region "$REGIAO" --owners self \
    --filters "Name=name,Values=fabiano-homolog-base-*" \
    --query 'Images[].ImageId' --output text); do
  echo "desregistrando AMI ${AMI}"
  SNAPS=$(aws ec2 describe-images --region "$REGIAO" --image-ids "$AMI" \
    --query 'Images[].BlockDeviceMappings[].Ebs.SnapshotId' --output text)
  aws ec2 deregister-image --region "$REGIAO" --image-id "$AMI"
  # O snapshot EBS NAO some junto com a AMI. Esse e o vazamento de custo classico
  # de quem cria imagem por script: a AMI some da lista e o disco continua sendo
  # cobrado, invisivel, para sempre.
  for S in $SNAPS; do
    echo "  apagando snapshot EBS ${S}"
    aws ec2 delete-snapshot --region "$REGIAO" --snapshot-id "$S" || true
  done
done

for SNAP in $(aws rds describe-db-snapshots --region "$REGIAO" --snapshot-type manual \
    --query 'DBSnapshots[?starts_with(DBSnapshotIdentifier, `homolog-base-`)].DBSnapshotIdentifier' \
    --output text); do
  echo "apagando snapshot RDS ${SNAP}"
  aws rds delete-db-snapshot --region "$REGIAO" --db-snapshot-identifier "$SNAP" >/dev/null
done

cat <<FIM

=============================================================================
 HOMOLOG DERRUBADO
=============================================================================
 EC2 terminada, banco apagado, AMIs e snapshots de base removidos.

 O QUE FICA, de proposito:
   Elastic IP eipalloc-053acd67132fed0af (54.197.175.159)
   ~US\$ 3,60/mes, com o DNS de api-hml e grafana-hml ja apontando para ele.
   Liberar significaria perder o endereco e refazer DNS e certificado depois.

 Custo corrente de homolog a partir de agora: so o Elastic IP.

 -----------------------------------------------------------------------------
 !! FALTA UM PASSO, E ELE NAO E OPCIONAL !!
 -----------------------------------------------------------------------------
 GitHub > Settings > Secrets and variables > Actions > aba VARIABLES
   HOMOLOG_ATIVO = false

 Sem isso, o proximo push na develop tenta deployar numa maquina que nao existe
 mais e o job morre com 'dial tcp ***:22: i/o timeout'. Aconteceu em 09/08/2026.

 A falha e barulhenta de proposito — melhor que passar verde sem deployar —
 mas e ruido evitavel. Automatizar essa troca e o FABIANO-33 etapa 6b.

 Conferir daqui a alguns minutos que nada ficou para tras:
   aws ec2 describe-instances --region ${REGIAO} \\
     --filters "Name=tag:Ambiente,Values=homolog" \\
     --query 'Reservations[].Instances[].[InstanceId,State.Name]' --output table
=============================================================================
FIM
