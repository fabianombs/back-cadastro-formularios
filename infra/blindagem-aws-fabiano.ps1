# =============================================================================
# Blindagem AWS - Projeto Fabiano (FABIANO-20 / FABIANO-4)  -- versao PowerShell
# =============================================================================
# ESTE SCRIPT ALTERA RECURSOS DE PRODUCAO.
#
# Rodar SOMENTE depois de ler a saida do diagnostico-aws-fabiano.ps1.
# Cada etapa pede confirmacao. Ordem crescente de impacto:
#
#   ETAPA 1  snapshot manual        -> zero impacto, sem reboot
#   ETAPA 2  deletion protection    -> zero impacto, sem reboot
#   ETAPA 3  janelas                -> zero impacto, sem reboot
#   ETAPA 4  retencao de backup     -> *** PROVOCA REBOOT CURTO *** (janela!)
#
# Uso:  .\infra\blindagem-aws-fabiano.ps1 | Tee-Object blindagem-aws.txt
# =============================================================================

$ErrorActionPreference = "Continue"
$env:AWS_DEFAULT_REGION = "us-east-1"
$DB_ID   = "poc-fabiano-db"
$SNAP_ID = "poc-fabiano-db-pre84-" + (Get-Date -Format "yyyyMMdd-HHmm")

# Pede confirmacao explicita antes de cada alteracao em producao
function Confirmar($msg) {
    Write-Host ""
    Write-Host ">>> $msg" -ForegroundColor Yellow
    $r = Read-Host ">>> Confirma? [sim/N]"
    return ($r -eq "sim")
}

Write-Host "############################################################"
Write-Host "# BLINDAGEM - $DB_ID"
Write-Host "############################################################"

# -----------------------------------------------------------------------------
# ETAPA 1 - Snapshot manual (rede de seguranca de tudo que vem depois)
# -----------------------------------------------------------------------------
# Snapshot manual nao expira sozinho, diferente do automatico.
if (Confirmar "ETAPA 1: criar snapshot manual '$SNAP_ID' (sem reboot, sem downtime)") {
    aws rds create-db-snapshot `
      --db-instance-identifier $DB_ID `
      --db-snapshot-identifier $SNAP_ID `
      --tags Key=Project,Value=poc-fabiano Key=Motivo,Value=pre-upgrade-8.4

    Write-Host "Aguardando o snapshot ficar disponivel (pode levar alguns minutos)..."
    aws rds wait db-snapshot-available --db-snapshot-identifier $SNAP_ID
    Write-Host "OK - snapshot $SNAP_ID disponivel." -ForegroundColor Green
} else { Write-Host "Etapa 1 pulada." }

# -----------------------------------------------------------------------------
# ETAPA 2 - Protecao contra delecao acidental
# -----------------------------------------------------------------------------
# Com isso ligado, um 'terraform destroy' ou clique errado no console e recusado.
if (Confirmar "ETAPA 2: ligar deletion-protection (sem reboot, sem downtime)") {
    aws rds modify-db-instance `
      --db-instance-identifier $DB_ID `
      --deletion-protection `
      --apply-immediately
    Write-Host "OK - deletion protection ligada." -ForegroundColor Green
} else { Write-Host "Etapa 2 pulada." }

# -----------------------------------------------------------------------------
# ETAPA 3 - Janelas previsiveis
# -----------------------------------------------------------------------------
# Horarios em UTC. Brasil e UTC-3, entao:
#   backup      06:00-06:30 UTC = 03:00-03:30 de Brasilia
#   manutencao  dom 07:00-08:00 UTC = domingo 04:00-05:00 de Brasilia
if (Confirmar "ETAPA 3: definir janelas de backup e manutencao (sem reboot)") {
    aws rds modify-db-instance `
      --db-instance-identifier $DB_ID `
      --preferred-backup-window "06:00-06:30" `
      --preferred-maintenance-window "sun:07:00-sun:08:00" `
      --apply-immediately
    Write-Host "OK - janelas definidas." -ForegroundColor Green
} else { Write-Host "Etapa 3 pulada." }

# -----------------------------------------------------------------------------
# ETAPA 4 - Backup automatico + point-in-time recovery  *** REBOOT ***
# -----------------------------------------------------------------------------
# Sair de retencao 0 provoca reboot curto (aplicacao cai por alguns minutos).
# E a etapa mais valiosa: liga o point-in-time recovery, que permite restaurar
# o banco em qualquer segundo dos ultimos 7 dias.
Write-Host ""
Write-Host "############################################################" -ForegroundColor Red
Write-Host "# ATENCAO - a proxima etapa PROVOCA REBOOT da instancia."     -ForegroundColor Red
Write-Host "# A aplicacao fica indisponivel por alguns minutos."          -ForegroundColor Red
Write-Host "# So siga se estiver fora do horario de uso do Fabiano."      -ForegroundColor Red
Write-Host "############################################################" -ForegroundColor Red

if (Confirmar "ETAPA 4: ligar backup automatico com retencao de 7 dias (PROVOCA REBOOT)") {
    aws rds modify-db-instance `
      --db-instance-identifier $DB_ID `
      --backup-retention-period 7 `
      --apply-immediately

    Write-Host "Aguardando a instancia voltar para 'available'..."
    Start-Sleep -Seconds 30
    aws rds wait db-instance-available --db-instance-identifier $DB_ID
    Write-Host "OK - instancia de volta." -ForegroundColor Green
} else { Write-Host "Etapa 4 pulada. Rode depois, na janela combinada." }

# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "=== ESTADO FINAL ===" -ForegroundColor Cyan
aws rds describe-db-instances --db-instance-identifier $DB_ID `
  --query "DBInstances[0].{Status:DBInstanceStatus,RetencaoBackupDias:BackupRetentionPeriod,JanelaBackup:PreferredBackupWindow,JanelaManutencao:PreferredMaintenanceWindow,ProtecaoDelecao:DeletionProtection,RestauravelAte:LatestRestorableTime}" `
  --output table

Write-Host ""
Write-Host "Se RetencaoBackupDias = 7 e RestauravelAte trouxe uma data, o point-in-time"
Write-Host "recovery esta ativo. Ai sim da para pensar no upgrade para 8.4."
