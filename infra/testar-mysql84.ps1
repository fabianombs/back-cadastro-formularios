# =============================================================================
# Teste de compatibilidade com MySQL 8.4 (FABIANO-7)
# =============================================================================
# Responde a pergunta que mais importa antes do upgrade do RDS:
# "as 60 migrations do Flyway aplicam do zero no MySQL 8.4, e o Hibernate
#  valida o schema resultante?"
#
# Nao toca em producao. Nao toca no banco de desenvolvimento do dia a dia.
# Sobe um MySQL 8.4 descartavel na porta 3308, aplica tudo, e derruba.
#
# Uso:  .\infra\testar-mysql84.ps1
#
# NOTAS DE POWERSHELL (aprendidas na marra):
#  - Arquivo em ASCII puro: o PS 5.1 le UTF-8 sem BOM como ANSI e embaralha
#    acento/travessao, quebrando o parser.
#  - SEM $ErrorActionPreference = "Stop": com ele, qualquer coisa que um
#    comando nativo escreva em stderr (ate um simples Warning) vira erro
#    fatal. Aqui o controle e por $LASTEXITCODE, explicitamente.
#  - Warning do cliente mysql na stderr: filtrado com Where-Object, nao
#    suprimido na origem (MYSQL_PWD nao atravessa bem o docker exec).
# =============================================================================

$ErrorActionPreference = "Continue"

function Parar($msg) {
    Write-Host $msg -ForegroundColor Red
    exit 1
}

Write-Host "############################################################"
Write-Host "# TESTE DE COMPATIBILIDADE - MySQL 8.4"
Write-Host "############################################################"
Write-Host ""

# -----------------------------------------------------------------------------
Write-Host "[1/4] Subindo MySQL 8.4 descartavel na porta 3308..." -ForegroundColor Cyan
# -----------------------------------------------------------------------------
# 'down -v' antes garante banco vazio: o teste so vale se o Flyway aplicar
# as migrations DO ZERO, como aconteceria numa base nova.
docker compose -f docker-compose.dev.yml --profile migracao down -v 2>&1 | Out-Null
docker compose -f docker-compose.dev.yml --profile migracao up -d mysql-migracao 2>&1 | Write-Host
if ($LASTEXITCODE -ne 0) { Parar "Falha ao subir o container. O Docker Desktop esta rodando?" }

Write-Host "[2/4] Aguardando o banco aceitar conexao..." -ForegroundColor Cyan
$pronto = $false
foreach ($i in 1..40) {
    $status = (docker inspect --format "{{.State.Health.Status}}" fabiano-mysql84-migracao 2>&1) -join ""
    if ($status -match "healthy") { $pronto = $true; break }
    Start-Sleep -Seconds 3
    Write-Host ("  ... {0}s" -f ($i * 3))
}
if (-not $pronto) { Parar "MySQL nao ficou pronto. Veja: docker logs fabiano-mysql84-migracao" }
Write-Host "  banco pronto." -ForegroundColor Green

# Confirma que e mesmo 8.4 SEM autenticar no banco: pergunta ao Docker qual
# imagem o container esta rodando. Evita senha, evita warning na stderr e
# evita depender do cliente mysql de dentro do container.
$imagem = (docker inspect --format "{{.Config.Image}}" fabiano-mysql84-migracao 2>&1) -join ""
Write-Host ("  imagem do container: {0}" -f $imagem) -ForegroundColor Green
if ($imagem -notmatch "8\.4") {
    Parar "Esperava mysql:8.4, o container esta em '$imagem'. Confira o docker-compose.dev.yml."
}

# Versao reportada pelo servidor, so como confirmacao extra. O cliente escreve
# um Warning na stderr por causa do -p; filtramos a linha em vez de tentar
# suprimir na origem (MYSQL_PWD nao atravessa bem o docker exec pelo PowerShell).
$saida  = docker exec fabiano-mysql84-migracao mysql -uroot -proot -N -B -e "SELECT VERSION();" 2>&1
$versao = ($saida | Where-Object { "$_" -notmatch "Warning" } | Select-Object -First 1)
if ($versao) { Write-Host ("  versao do servidor: {0}" -f $versao) -ForegroundColor Green }

# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "[3/4] Subindo a aplicacao contra o 8.4 (Flyway + ddl-auto=validate)..." -ForegroundColor Cyan
# -----------------------------------------------------------------------------
# Configuracao por variavel de ambiente em vez de -Dspring-boot.run.arguments:
# o Spring Boot faz relaxed binding (SPRING_DATASOURCE_URL -> spring.datasource.url)
# e assim se evita o problema de aspas do PowerShell repassando para o .cmd.
$env:SPRING_PROFILES_ACTIVE            = "dev"
$env:SPRING_DATASOURCE_URL             = "jdbc:mysql://localhost:3308/fabiano_migracao_check?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false"
$env:SPRING_DATASOURCE_USERNAME        = "root"
$env:SPRING_DATASOURCE_PASSWORD        = "root"
# validate e o mesmo de producao: o Hibernate NAO cria nem altera nada, so
# confere se o schema que o Flyway produziu bate com as entidades.
$env:SPRING_JPA_HIBERNATE_DDL_AUTO     = "validate"
$env:SPRING_FLYWAY_ENABLED             = "true"
$env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = "false"
$env:SPRING_FLYWAY_REPAIR_ON_MIGRATE   = "false"
$env:LOGGING_LEVEL_ORG_FLYWAYDB        = "INFO"
$env:JWT_SECRET                        = "segredo-local-de-teste-com-mais-de-32-caracteres"

Write-Host "  (assim que aparecer 'Started CadastroFabianoApplication' o teste passou;" -ForegroundColor DarkGray
Write-Host "   pode parar com Ctrl+C)" -ForegroundColor DarkGray
Write-Host ""

& .\mvnw.cmd spring-boot:run

Write-Host ""
Write-Host "############################################################"
Write-Host "# Como ler o resultado:"
Write-Host "#"
Write-Host "#  'Started CadastroFabianoApplication'  -> PASSOU."
Write-Host "#     As 60 migrations aplicaram no 8.4 e o schema bate com as"
Write-Host "#     entidades."
Write-Host "#"
Write-Host "#  Erro do Flyway                        -> alguma migration usa"
Write-Host "#     sintaxe removida no 8.4. O log diz qual arquivo e qual linha."
Write-Host "#"
Write-Host "#  'Schema-validation: ...'              -> o schema gerado nao bate"
Write-Host "#     com as entidades JPA."
Write-Host "#"
Write-Host "# Para limpar o container de teste depois:"
Write-Host "#   docker compose -f docker-compose.dev.yml --profile migracao down -v"
Write-Host "############################################################"
