# =============================================================================
# Ambiente local com dados REAIS de producao em MySQL 8.4 (FABIANO-7)
# =============================================================================
# Ensaia a migracao inteira antes de tocar no RDS:
#   1. sobe MySQL 8.4 local
#   2. restaura o dump de producao (que veio de um 8.0) dentro dele
#   3. sobe a aplicacao com ddl-auto=validate, igual producao
#
# O Flyway vai encontrar as 59 migrations ja aplicadas no dump e aplicar so a
# V60 - que e precisamente o que acontecera no RDS depois do upgrade.
#
# Uso:
#   .\infra\dev-local.ps1                 sobe banco + restaura + roda a app
#   .\infra\dev-local.ps1 -SomenteInfra   sobe banco e restaura, deixa a 8080 livre pra IDE
#   .\infra\dev-local.ps1 -SemRestaurar   nao mexe no banco, so roda a app
#   .\infra\dev-local.ps1 -Derrubar       para tudo e apaga o volume
#
# NOTAS DE POWERSHELL: arquivo em ASCII puro; ErrorActionPreference = Continue
# (com Stop, stderr de comando nativo vira erro fatal); controle por
# $LASTEXITCODE; sempre .\mvnw.cmd, nunca .\mvnw.
# =============================================================================

param(
    [switch]$SomenteInfra,
    [switch]$SemRestaurar,
    [switch]$Derrubar,
    [string]$Dump = "C:\projetos\Fabiano\fabiano-real-20260802-1508.sql.gz"
)

$ErrorActionPreference = "Continue"

$CONTAINER = "fabiano-mysql-dev"
$BANCO     = "teste_fabiano_cadastro2"
$PORTA     = 3307

function Parar($msg) { Write-Host $msg -ForegroundColor Red; exit 1 }
function Titulo($msg) { Write-Host ""; Write-Host $msg -ForegroundColor Cyan }

if ($Derrubar) {
    Titulo "Derrubando o ambiente local..."
    docker compose -f docker-compose.dev.yml down -v 2>&1 | Write-Host
    Write-Host "Ambiente removido." -ForegroundColor Green
    exit 0
}

Write-Host "############################################################"
Write-Host "# AMBIENTE LOCAL - MySQL 8.4 com dados de producao"
Write-Host "############################################################"

# -----------------------------------------------------------------------------
Titulo "[1/4] Subindo MySQL 8.4 e Dozzle..."
# -----------------------------------------------------------------------------
docker compose -f docker-compose.dev.yml up -d mysql dozzle 2>&1 | Write-Host
if ($LASTEXITCODE -ne 0) { Parar "Falha ao subir. O Docker Desktop esta rodando?" }

Write-Host "Aguardando o banco ficar pronto..."
$pronto = $false
foreach ($i in 1..40) {
    $status = (docker inspect --format "{{.State.Health.Status}}" $CONTAINER 2>&1) -join ""
    if ($status -match "healthy") { $pronto = $true; break }
    Start-Sleep -Seconds 3
    Write-Host ("  ... {0}s" -f ($i * 3))
}
if (-not $pronto) { Parar "MySQL nao ficou pronto. Veja: docker logs $CONTAINER" }

$imagem = (docker inspect --format "{{.Config.Image}}" $CONTAINER 2>&1) -join ""
Write-Host ("  pronto - imagem {0}, porta {1}" -f $imagem, $PORTA) -ForegroundColor Green
if ($imagem -notmatch "8\.4") { Parar "Esperava mysql:8.4, veio '$imagem'." }

# -----------------------------------------------------------------------------
if (-not $SemRestaurar) {
    Titulo "[2/4] Restaurando o dump de producao..."
# -----------------------------------------------------------------------------
    if (-not (Test-Path $Dump)) {
        Parar "Dump nao encontrado em '$Dump'. Passe o caminho com -Dump <arquivo>."
    }
    $tam = [math]::Round((Get-Item $Dump).Length / 1KB, 0)
    Write-Host ("  arquivo: {0} ({1} KB)" -f (Split-Path $Dump -Leaf), $tam)

    # O .gz e descompactado DENTRO do container: assim nao e preciso ter gzip
    # nem 7zip instalado no Windows.
    docker cp $Dump "${CONTAINER}:/tmp/dump.sql.gz" 2>&1 | Write-Host
    if ($LASTEXITCODE -ne 0) { Parar "Falha ao copiar o dump para o container." }

    # Banco do zero: o dump nao traz CREATE DATABASE (mysqldump de base unica).
    # 'sh -c' com a linha inteira: o PowerShell entrega UMA string e quem
    # interpreta e o shell do container. Passar -proot direto pelo docker exec
    # chegava corrompido e dava "Access denied".
    docker exec $CONTAINER sh -c "export MYSQL_PWD=root; mysql -uroot -e 'DROP DATABASE IF EXISTS $BANCO; CREATE DATABASE $BANCO CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;'" 2>&1 |
        Where-Object { "$_" -notmatch "Warning" } | Write-Host
    if ($LASTEXITCODE -ne 0) { Parar "Nao consegui recriar o banco no container." }

    Write-Host "  restaurando (pode levar alguns segundos)..."
    docker exec $CONTAINER sh -c "export MYSQL_PWD=root; gunzip -c /tmp/dump.sql.gz | mysql -uroot $BANCO" 2>&1 |
        Where-Object { "$_" -notmatch "Warning" } | Write-Host
    if ($LASTEXITCODE -ne 0) { Parar "Falha ao restaurar o dump." }

    # Conferencia: o dump so vale se os dados chegaram de fato.
    $tabelas = (docker exec $CONTAINER sh -c "export MYSQL_PWD=root; mysql -uroot -N -B $BANCO -e 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()'" 2>&1 |
        Where-Object { "$_" -notmatch "Warning" } | Select-Object -First 1)
    $flyway = (docker exec $CONTAINER sh -c "export MYSQL_PWD=root; mysql -uroot -N -B $BANCO -e 'SELECT COALESCE(MAX(CAST(version AS UNSIGNED)),0) FROM flyway_schema_history'" 2>&1 |
        Where-Object { "$_" -notmatch "Warning" } | Select-Object -First 1)
    $presenca = (docker exec $CONTAINER sh -c "export MYSQL_PWD=root; mysql -uroot -N -B $BANCO -e 'SELECT COUNT(*) FROM attendance_records'" 2>&1 |
        Where-Object { "$_" -notmatch "Warning" } | Select-Object -First 1)

    # Extrai o numero com regex: se a consulta falhar, a saida e texto de erro
    # e um cast [int] direto estoura antes de conseguirmos dar um aviso decente.
    function Numero($v) { if ("$v" -match '(\d+)') { [int]$Matches[1] } else { -1 } }
    $nTabelas  = Numero $tabelas
    $nFlyway   = Numero $flyway
    $nPresenca = Numero $presenca

    Write-Host ("  tabelas restauradas    : {0}   (esperado 24)" -f $nTabelas) -ForegroundColor Green
    Write-Host ("  ultima migration       : V{0}  (esperado 59)" -f $nFlyway) -ForegroundColor Green
    Write-Host ("  registros de presenca  : {0}  (esperado ~4342)" -f $nPresenca) -ForegroundColor Green

    if ($nTabelas -lt 20) {
        Write-Host ""
        Write-Host "Saida bruta das consultas (para diagnostico):" -ForegroundColor Yellow
        Write-Host ("  tabelas : {0}" -f $tabelas)
        Write-Host ("  flyway  : {0}" -f $flyway)
        Write-Host ("  presenca: {0}" -f $presenca)
        Parar "Restauracao incompleta - poucas tabelas."
    }
}

# -----------------------------------------------------------------------------
Titulo "[3/4] Configurando a aplicacao..."
# -----------------------------------------------------------------------------
# Por variavel de ambiente (relaxed binding do Spring Boot) em vez de
# -Dspring-boot.run.arguments: evita o inferno de aspas do PowerShell.
$env:SPRING_PROFILES_ACTIVE            = "dev"
$env:SPRING_DATASOURCE_URL             = "jdbc:mysql://localhost:$PORTA/$($BANCO)?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"
$env:SPRING_DATASOURCE_USERNAME        = "root"
$env:SPRING_DATASOURCE_PASSWORD        = "root"
# validate, igual producao: o Hibernate nao cria nem altera nada, so confere
# se o schema bate com as entidades depois que o Flyway terminar.
$env:SPRING_JPA_HIBERNATE_DDL_AUTO     = "validate"
$env:SPRING_FLYWAY_ENABLED             = "true"
$env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = "false"
$env:SPRING_FLYWAY_REPAIR_ON_MIGRATE   = "false"
$env:LOGGING_LEVEL_ORG_FLYWAYDB        = "INFO"
$env:JWT_SECRET                        = "segredo-local-de-teste-com-mais-de-32-caracteres"
$env:CORS_ALLOWED_ORIGINS              = "http://localhost:4200"
$env:APP_BASE_URL                      = "http://localhost:8080"
$env:APP_FRONTEND_URL                  = "http://localhost:4200"

Write-Host "  banco    : localhost:$PORTA/$BANCO"
Write-Host "  Dozzle   : http://localhost:9999"
Write-Host "  Swagger  : http://localhost:8080/swagger-ui.html"

if ($SomenteInfra) {
    Write-Host ""
    Write-Host "Infra no ar. A porta 8080 esta livre para voce rodar pela IDE." -ForegroundColor Green
    Write-Host "Se rodar pela IDE, aponte o datasource para:" -ForegroundColor Green
    Write-Host "  jdbc:mysql://localhost:$PORTA/$BANCO  (root / root)" -ForegroundColor Green
    exit 0
}

# -----------------------------------------------------------------------------
# Porta 8080 ocupada e o tropeco mais comum aqui. O Spring so falha DEPOIS de
# subir o contexto inteiro - Flyway, Hibernate, pool de conexao - e a causa
# real fica soterrada em dezenas de linhas de log. Checar antes evita o ciclo.
# -----------------------------------------------------------------------------
$pidOcupando = $null
try {
    # Get-NetTCPConnection LANCA erro quando nao encontra nada, em vez de
    # devolver lista vazia - por isso try/catch, e nao checagem de contagem.
    $pidOcupando = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction Stop |
        Select-Object -First 1 -ExpandProperty OwningProcess
} catch {
    $pidOcupando = $null
}

if ($pidOcupando) {
    $nomeProc = "desconhecido"
    $proc = Get-Process -Id $pidOcupando -ErrorAction SilentlyContinue
    if ($proc) { $nomeProc = $proc.ProcessName }

    Write-Host ""
    Write-Host "  A porta 8080 ja esta ocupada pelo PID $pidOcupando ($nomeProc)." -ForegroundColor Yellow
    Write-Host "  Quase sempre e uma execucao anterior desta mesma aplicacao." -ForegroundColor Yellow
    $resposta = Read-Host "  Encerrar esse processo e continuar? (s/N)"
    if ($resposta -eq "s") {
        Stop-Process -Id $pidOcupando -Force
        # A porta nao e liberada no mesmo instante em que o processo morre.
        Start-Sleep -Seconds 2
        Write-Host "  PID $pidOcupando encerrado." -ForegroundColor Green
    } else {
        Write-Host "  Cancelado. Libere a porta 8080 e rode de novo." -ForegroundColor Red
        exit 1
    }
}

# -----------------------------------------------------------------------------
Titulo "[4/4] Subindo a aplicacao (Flyway vai aplicar a V60)..."
# -----------------------------------------------------------------------------
Write-Host "  (quando aparecer 'Started CadastroFabianoApplication', esta no ar)" -ForegroundColor DarkGray
Write-Host ""

& .\mvnw.cmd spring-boot:run
