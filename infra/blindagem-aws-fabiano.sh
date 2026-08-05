#!/usr/bin/env bash
# =============================================================================
# Blindagem AWS — Projeto Fabiano (FABIANO-20 / FABIANO-4)
# =============================================================================
# ESTE SCRIPT ALTERA RECURSOS DE PRODUÇÃO.
#
# Rodar SOMENTE depois de ler a saída do diagnostico-aws-fabiano.sh.
# Cada etapa pede confirmação. As etapas estão em ordem crescente de impacto:
#
#   ETAPA 1  snapshot manual        -> zero impacto, sem reboot
#   ETAPA 2  deletion protection    -> zero impacto, sem reboot
#   ETAPA 3  janela de manutenção   -> zero impacto, sem reboot
#   ETAPA 4  retenção de backup     -> *** PROVOCA REBOOT CURTO *** (janela!)
#
# Uso:  bash blindagem-aws-fabiano.sh 2>&1 | tee blindagem-aws.txt
# =============================================================================

set -uo pipefail

export AWS_DEFAULT_REGION=us-east-1
DB_ID="poc-fabiano-db"
SNAP_ID="poc-fabiano-db-pre84-$(date +%Y%m%d-%H%M)"

# Pede confirmação explícita antes de cada alteração em produção
confirmar() {
  echo
  echo ">>> $1"
  read -r -p ">>> Confirma? [sim/N] " R
  [ "$R" = "sim" ]
}

echo "############################################################"
echo "# BLINDAGEM — $DB_ID"
echo "############################################################"

# -----------------------------------------------------------------------------
# ETAPA 1 — Snapshot manual (a rede de segurança de tudo que vem depois)
# -----------------------------------------------------------------------------
# Snapshot manual não expira sozinho, diferente do automático.
if confirmar "ETAPA 1: criar snapshot manual '$SNAP_ID' (sem reboot, sem downtime)"; then
  aws rds create-db-snapshot \
    --db-instance-identifier "$DB_ID" \
    --db-snapshot-identifier "$SNAP_ID" \
    --tags Key=Project,Value=poc-fabiano Key=Motivo,Value=pre-upgrade-8.4

  echo "Aguardando o snapshot ficar disponível (pode levar alguns minutos)..."
  aws rds wait db-snapshot-available --db-snapshot-identifier "$SNAP_ID"
  echo "OK — snapshot $SNAP_ID disponível."
else
  echo "Etapa 1 pulada."
fi

# -----------------------------------------------------------------------------
# ETAPA 2 — Proteção contra deleção acidental
# -----------------------------------------------------------------------------
# Com isso ligado, um 'terraform destroy' ou clique errado no console é recusado.
if confirmar "ETAPA 2: ligar deletion-protection (sem reboot, sem downtime)"; then
  aws rds modify-db-instance \
    --db-instance-identifier "$DB_ID" \
    --deletion-protection \
    --apply-immediately
  echo "OK — deletion protection ligada."
else
  echo "Etapa 2 pulada."
fi

# -----------------------------------------------------------------------------
# ETAPA 3 — Janelas previsíveis
# -----------------------------------------------------------------------------
# Horários em UTC. Brasil está em UTC-3, então:
#   backup      06:00-06:30 UTC = 03:00-03:30 de Brasília
#   manutenção  dom 07:00-08:00 UTC = domingo 04:00-05:00 de Brasília
if confirmar "ETAPA 3: definir janelas de backup e manutenção (sem reboot)"; then
  aws rds modify-db-instance \
    --db-instance-identifier "$DB_ID" \
    --preferred-backup-window "06:00-06:30" \
    --preferred-maintenance-window "sun:07:00-sun:08:00" \
    --apply-immediately
  echo "OK — janelas definidas."
else
  echo "Etapa 3 pulada."
fi

# -----------------------------------------------------------------------------
# ETAPA 4 — Backup automático + point-in-time recovery  *** REBOOT ***
# -----------------------------------------------------------------------------
# Sair de retenção 0 provoca um reboot curto da instância (a aplicação cai por
# alguns minutos). Fazer fora do horário de uso.
#
# É a etapa mais valiosa do script: liga o point-in-time recovery, que permite
# restaurar o banco em qualquer segundo dos últimos 7 dias.
echo
echo "############################################################"
echo "# ATENÇÃO — a próxima etapa PROVOCA REBOOT da instância."
echo "# A aplicação fica indisponível por alguns minutos."
echo "# Só siga se estiver fora do horário de uso do Fabiano."
echo "############################################################"

if confirmar "ETAPA 4: ligar backup automático com retenção de 7 dias (PROVOCA REBOOT)"; then
  aws rds modify-db-instance \
    --db-instance-identifier "$DB_ID" \
    --backup-retention-period 7 \
    --apply-immediately

  echo "Aguardando a instância voltar para 'available'..."
  sleep 30
  aws rds wait db-instance-available --db-instance-identifier "$DB_ID"
  echo "OK — instância de volta."
else
  echo "Etapa 4 pulada. Rode depois, na janela combinada."
fi

# -----------------------------------------------------------------------------
# Conferência final
# -----------------------------------------------------------------------------
echo
echo "=== ESTADO FINAL ==="
aws rds describe-db-instances --db-instance-identifier "$DB_ID" \
  --query "DBInstances[0].{
      Status:DBInstanceStatus,
      RetencaoBackupDias:BackupRetentionPeriod,
      JanelaBackup:PreferredBackupWindow,
      JanelaManutencao:PreferredMaintenanceWindow,
      ProtecaoDelecao:DeletionProtection,
      RestauravelAte:LatestRestorableTime
  }" --output table

echo
echo "Se RetencaoBackupDias = 7 e RestauravelAte trouxe uma data, o point-in-time"
echo "recovery está ativo. Aí sim dá para pensar no upgrade para 8.4."
