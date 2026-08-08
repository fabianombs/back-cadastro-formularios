# =============================================================================
# Diagnostico AWS - Projeto Fabiano (FABIANO-20 / FABIANO-4)  -- versao PowerShell
# =============================================================================
# SOMENTE LEITURA. Nenhum comando aqui altera qualquer recurso.
# Todos os verbos sao describe-* / get-* / list-*.
#
# Uso:  .\infra\diagnostico-aws-fabiano.ps1 | Tee-Object diagnostico-aws.txt
#
# Se o PowerShell recusar por politica de execucao, rode antes:
#   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
# =============================================================================

$ErrorActionPreference = "Continue"
$env:AWS_DEFAULT_REGION = "us-east-1"
$DB_ID = "poc-fabiano-db"

function Titulo($t) {
    Write-Host ""
    Write-Host "=== $t ===" -ForegroundColor Cyan
}

Write-Host "############################################################"
Write-Host "# DIAGNOSTICO AWS - Projeto Fabiano"
Write-Host "# Data: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "############################################################"

# -----------------------------------------------------------------------------
Titulo "[0] IDENTIDADE / CLI"
# -----------------------------------------------------------------------------
aws --version
aws sts get-caller-identity --output table

# Se o comando acima falhar com NoCredentials, pare aqui: nada abaixo funciona.
$identidade = aws sts get-caller-identity --output text 2>$null
if (-not $identidade) {
    Write-Host ""
    Write-Host "CREDENCIAIS NAO CONFIGURADAS. Configure antes de continuar." -ForegroundColor Red
    Write-Host "Veja as instrucoes no card FABIANO-20." -ForegroundColor Red
    exit 1
}

# -----------------------------------------------------------------------------
Titulo "[1] RDS - ESTADO DO BACKUP (o item critico)"
# -----------------------------------------------------------------------------
# BackupRetentionPeriod = 0 significa SEM backup automatico e SEM point-in-time recovery.
aws rds describe-db-instances --db-instance-identifier $DB_ID `
  --query "DBInstances[0].{Engine:EngineVersion,Classe:DBInstanceClass,Status:DBInstanceStatus,RetencaoBackupDias:BackupRetentionPeriod,JanelaBackup:PreferredBackupWindow,JanelaManutencao:PreferredMaintenanceWindow,ProtecaoDelecao:DeletionProtection,MultiAZ:MultiAZ,StorageGB:AllocatedStorage,TipoStorage:StorageType,Criptografado:StorageEncrypted,Publico:PubliclyAccessible,ParameterGroup:DBParameterGroups[0].DBParameterGroupName,Endpoint:Endpoint.Address}" `
  --output table

# -----------------------------------------------------------------------------
Titulo "[2] SNAPSHOTS EXISTENTES - manuais"
# -----------------------------------------------------------------------------
aws rds describe-db-snapshots --db-instance-identifier $DB_ID --snapshot-type manual `
  --query "DBSnapshots[].{Nome:DBSnapshotIdentifier,Quando:SnapshotCreateTime,Status:Status,GB:AllocatedStorage}" `
  --output table

Titulo "[2b] SNAPSHOTS EXISTENTES - automaticos (vazio confirma backup desligado)"
aws rds describe-db-snapshots --db-instance-identifier $DB_ID --snapshot-type automated `
  --query "DBSnapshots[].{Nome:DBSnapshotIdentifier,Quando:SnapshotCreateTime,Status:Status}" `
  --output table

# -----------------------------------------------------------------------------
Titulo "[3] JANELA DE POINT-IN-TIME RECOVERY"
# -----------------------------------------------------------------------------
# Se RestauravelAte vier vazio/None, NAO existe PITR.
aws rds describe-db-instances --db-instance-identifier $DB_ID `
  --query "DBInstances[0].{RestauravelAte:LatestRestorableTime,InstanciaCriadaEm:InstanceCreateTime}" `
  --output table

# -----------------------------------------------------------------------------
Titulo "[4] CAMINHOS DE UPGRADE DISPONIVEIS"
# -----------------------------------------------------------------------------
$CURRENT_VER = aws rds describe-db-instances --db-instance-identifier $DB_ID `
  --query "DBInstances[0].EngineVersion" --output text
Write-Host "Versao atual: $CURRENT_VER"

aws rds describe-db-engine-versions --engine mysql --engine-version $CURRENT_VER `
  --query "DBEngineVersions[0].ValidUpgradeTarget[?IsMajorVersionUpgrade==``true``].{Versao:EngineVersion,Descricao:Description}" `
  --output table

# -----------------------------------------------------------------------------
Titulo "[5] ESPACO LIVRE NO RDS (minimo 2 GiB para o upgrade)"
# -----------------------------------------------------------------------------
$inicio = (Get-Date).ToUniversalTime().AddHours(-2).ToString("yyyy-MM-ddTHH:mm:ssZ")
$fim    = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

aws cloudwatch get-metric-statistics `
  --namespace AWS/RDS --metric-name FreeStorageSpace `
  --dimensions Name=DBInstanceIdentifier,Value=$DB_ID `
  --start-time $inicio --end-time $fim `
  --period 3600 --statistics Minimum `
  --query "Datapoints[].{Quando:Timestamp,LivreBytes:Minimum}" --output table
Write-Host "(dividir LivreBytes por 1073741824 para ler em GiB)"

# -----------------------------------------------------------------------------
Titulo "[6] EC2 DO PROJETO"
# -----------------------------------------------------------------------------
aws ec2 describe-instances `
  --filters "Name=tag:Project,Values=poc-fabiano" "Name=instance-state-name,Values=running,stopped" `
  --query "Reservations[].Instances[].{Id:InstanceId,Tipo:InstanceType,Estado:State.Name,IPPublico:PublicIpAddress,AMI:ImageId,Volume:BlockDeviceMappings[0].Ebs.VolumeId}" `
  --output table

Titulo "[6b] VOLUME EBS"
$VOL = aws ec2 describe-instances `
  --filters "Name=tag:Project,Values=poc-fabiano" "Name=instance-state-name,Values=running,stopped" `
  --query "Reservations[0].Instances[0].BlockDeviceMappings[0].Ebs.VolumeId" --output text
if ($VOL -and $VOL -ne "None") {
    aws ec2 describe-volumes --volume-ids $VOL `
      --query "Volumes[0].{Id:VolumeId,GB:Size,Tipo:VolumeType,IOPS:Iops}" --output table
}

# -----------------------------------------------------------------------------
Titulo "[7] CUSTO DE EXTENDED SUPPORT (ultimos 30 dias)"
# -----------------------------------------------------------------------------
# Requer permissao ce:GetCostAndUsage. AccessDenied aqui nao e problema -
# da para ver o mesmo dado no console do Cost Explorer.
$ini = (Get-Date).ToUniversalTime().AddDays(-30).ToString("yyyy-MM-dd")
$hoje = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd")

aws ce get-cost-and-usage `
  --time-period Start=$ini,End=$hoje `
  --granularity MONTHLY --metrics UnblendedCost `
  --filter '{\"Dimensions\":{\"Key\":\"SERVICE\",\"Values\":[\"Amazon Relational Database Service\"]}}' `
  --group-by Type=DIMENSION,Key=USAGE_TYPE `
  --query "ResultsByTime[].Groups[?contains(Keys[0],'ExtendedSupport')].{Item:Keys[0],Custo:Metrics.UnblendedCost.Amount}" `
  --output table

# -----------------------------------------------------------------------------
Titulo "[8] BUCKETS S3"
# -----------------------------------------------------------------------------
aws s3api list-buckets --query "Buckets[].{Nome:Name,Criado:CreationDate}" --output table

Write-Host ""
Write-Host "############################################################"
Write-Host "# FIM DO DIAGNOSTICO"
Write-Host "#"
Write-Host "# O numero que importa esta no bloco [1]: RetencaoBackupDias."
Write-Host "#   0  = sem backup automatico, sem point-in-time recovery"
Write-Host "#   >0 = backup automatico ligado"
Write-Host "############################################################"
