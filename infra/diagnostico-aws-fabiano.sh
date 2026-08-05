#!/usr/bin/env bash
# =============================================================================
# Diagnóstico AWS — Projeto Fabiano (FABIANO-20 / FABIANO-4)
# =============================================================================
# SOMENTE LEITURA. Nenhum comando aqui altera qualquer recurso.
# Rodar no Git Bash / WSL. No PowerShell as aspas do --query podem quebrar.
#
# Uso:  bash diagnostico-aws-fabiano.sh 2>&1 | tee diagnostico-aws.txt
# =============================================================================

set -uo pipefail

export AWS_DEFAULT_REGION=us-east-1
DB_ID="poc-fabiano-db"

echo "############################################################"
echo "# DIAGNÓSTICO AWS — Projeto Fabiano"
echo "# Data: $(date '+%Y-%m-%d %H:%M:%S')"
echo "############################################################"
echo

# -----------------------------------------------------------------------------
# 0. O CLI está configurado e apontando para a conta certa?
# -----------------------------------------------------------------------------
echo "=== [0] IDENTIDADE / CLI ==="
aws --version 2>&1
echo
# Se este comando falhar, o CLI não está configurado — nada abaixo vai funcionar.
aws sts get-caller-identity --output table 2>&1
echo

# -----------------------------------------------------------------------------
# 1. A PERGUNTA QUE IMPORTA: existe backup automático?
# -----------------------------------------------------------------------------
echo "=== [1] RDS — ESTADO DO BACKUP (o item crítico) ==="
# BackupRetentionPeriod = 0 significa SEM backup automático e SEM point-in-time recovery.
aws rds describe-db-instances --db-instance-identifier "$DB_ID" \
  --query "DBInstances[0].{
      Engine:EngineVersion,
      Classe:DBInstanceClass,
      Status:DBInstanceStatus,
      RetencaoBackupDias:BackupRetentionPeriod,
      JanelaBackup:PreferredBackupWindow,
      JanelaManutencao:PreferredMaintenanceWindow,
      ProtecaoDelecao:DeletionProtection,
      MultiAZ:MultiAZ,
      StorageGB:AllocatedStorage,
      TipoStorage:StorageType,
      Criptografado:StorageEncrypted,
      Publico:PubliclyAccessible,
      UpgradeMinorAuto:AutoMinorVersionUpgrade,
      ParameterGroup:DBParameterGroups[0].DBParameterGroupName,
      SyncParameterGroup:DBParameterGroups[0].ParameterApplyStatus,
      Endpoint:Endpoint.Address
  }" --output table 2>&1
echo

# -----------------------------------------------------------------------------
# 2. Que snapshots existem hoje?
# -----------------------------------------------------------------------------
echo "=== [2] SNAPSHOTS EXISTENTES ==="
echo "--- Manuais (não expiram sozinhos) ---"
aws rds describe-db-snapshots --db-instance-identifier "$DB_ID" \
  --snapshot-type manual \
  --query "DBSnapshots[].{Nome:DBSnapshotIdentifier,Quando:SnapshotCreateTime,Status:Status,GB:AllocatedStorage}" \
  --output table 2>&1
echo
echo "--- Automáticos (vazio = confirma que backup automático está desligado) ---"
aws rds describe-db-snapshots --db-instance-identifier "$DB_ID" \
  --snapshot-type automated \
  --query "DBSnapshots[].{Nome:DBSnapshotIdentifier,Quando:SnapshotCreateTime,Status:Status}" \
  --output table 2>&1
echo

# -----------------------------------------------------------------------------
# 3. Point-in-time recovery está disponível? Até que momento?
# -----------------------------------------------------------------------------
echo "=== [3] JANELA DE POINT-IN-TIME RECOVERY ==="
# Se LatestRestorableTime vier vazio/None, NÃO existe PITR.
aws rds describe-db-instances --db-instance-identifier "$DB_ID" \
  --query "DBInstances[0].{
      RestauravelAte:LatestRestorableTime,
      InstanciaCriadaEm:InstanceCreateTime
  }" --output table 2>&1
echo

# -----------------------------------------------------------------------------
# 4. Para qual versão 8.4 dá para subir a partir da versão atual?
# -----------------------------------------------------------------------------
echo "=== [4] CAMINHOS DE UPGRADE DISPONÍVEIS (a partir da versão atual) ==="
CURRENT_VER=$(aws rds describe-db-instances --db-instance-identifier "$DB_ID" \
  --query "DBInstances[0].EngineVersion" --output text 2>/dev/null)
echo "Versão atual: $CURRENT_VER"
echo
aws rds describe-db-engine-versions --engine mysql --engine-version "$CURRENT_VER" \
  --query "DBEngineVersions[0].ValidUpgradeTarget[?IsMajorVersionUpgrade==\`true\`].{Versao:EngineVersion,Descricao:Description}" \
  --output table 2>&1
echo

# -----------------------------------------------------------------------------
# 5. Espaço em disco no RDS (o upgrade exige no mínimo 2 GiB livres)
# -----------------------------------------------------------------------------
echo "=== [5] ESPAÇO LIVRE NO RDS (mínimo 2 GiB para o upgrade) ==="
aws cloudwatch get-metric-statistics \
  --namespace AWS/RDS --metric-name FreeStorageSpace \
  --dimensions Name=DBInstanceIdentifier,Value="$DB_ID" \
  --start-time "$(date -u -d '2 hours ago' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v-2H +%Y-%m-%dT%H:%M:%SZ)" \
  --end-time "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --period 3600 --statistics Minimum \
  --query "Datapoints[].{Quando:Timestamp,LivreGB:Minimum}" --output table 2>&1
echo "(dividir LivreGB por 1073741824 para ler em GiB)"
echo

# -----------------------------------------------------------------------------
# 6. A EC2 — confirmar tipo, disco e Elastic IP antes de redimensionar
# -----------------------------------------------------------------------------
echo "=== [6] EC2 DO PROJETO ==="
aws ec2 describe-instances \
  --filters "Name=tag:Project,Values=poc-fabiano" "Name=instance-state-name,Values=running,stopped" \
  --query "Reservations[].Instances[].{
      Id:InstanceId,
      Tipo:InstanceType,
      Estado:State.Name,
      IPPublico:PublicIpAddress,
      IPPrivado:PrivateIpAddress,
      AMI:ImageId,
      Volume:BlockDeviceMappings[0].Ebs.VolumeId
  }" --output table 2>&1
echo

echo "=== [6b] VOLUME EBS (tamanho e tipo atuais) ==="
VOL=$(aws ec2 describe-instances \
  --filters "Name=tag:Project,Values=poc-fabiano" "Name=instance-state-name,Values=running,stopped" \
  --query "Reservations[0].Instances[0].BlockDeviceMappings[0].Ebs.VolumeId" --output text 2>/dev/null)
if [ -n "$VOL" ] && [ "$VOL" != "None" ]; then
  aws ec2 describe-volumes --volume-ids "$VOL" \
    --query "Volumes[0].{Id:VolumeId,GB:Size,Tipo:VolumeType,IOPS:Iops}" --output table 2>&1
fi
echo

# -----------------------------------------------------------------------------
# 7. Quanto o Extended Support já custou? (últimos 30 dias)
# -----------------------------------------------------------------------------
echo "=== [7] CUSTO DE EXTENDED SUPPORT (últimos 30 dias) ==="
# Requer permissão ce:GetCostAndUsage. Se der AccessDenied, dá para ver no console.
aws ce get-cost-and-usage \
  --time-period Start="$(date -u -d '30 days ago' +%Y-%m-%d 2>/dev/null || date -u -v-30d +%Y-%m-%d)",End="$(date -u +%Y-%m-%d)" \
  --granularity MONTHLY --metrics "UnblendedCost" \
  --filter '{"Dimensions":{"Key":"SERVICE","Values":["Amazon Relational Database Service"]}}' \
  --group-by Type=DIMENSION,Key=USAGE_TYPE \
  --query "ResultsByTime[].Groups[?contains(Keys[0],'ExtendedSupport')].{Item:Keys[0],Custo:Metrics.UnblendedCost.Amount}" \
  --output table 2>&1
echo

# -----------------------------------------------------------------------------
# 8. Buckets S3 existentes (para saber se já há onde guardar backup offsite)
# -----------------------------------------------------------------------------
echo "=== [8] BUCKETS S3 ==="
aws s3api list-buckets --query "Buckets[].{Nome:Name,Criado:CreationDate}" --output table 2>&1
echo

echo "############################################################"
echo "# FIM DO DIAGNÓSTICO"
echo "#"
echo "# O número que importa está no bloco [1]: RetencaoBackupDias."
echo "#   0  = sem backup automático, sem point-in-time recovery -> agir hoje"
echo "#   >0 = backup automático ligado, respira fundo"
echo "############################################################"
