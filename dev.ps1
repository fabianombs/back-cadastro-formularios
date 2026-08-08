# =============================================================================
# dev.ps1 - Atalhos do ambiente local (Projeto Fabiano)
# =============================================================================
# Mesmo formato do dev.ps1 do back-pecas-intercambiaveis.
# Uso: clique direito > "Executar com PowerShell"   ou   .\dev.ps1
#
# Diferenca em relacao ao Pecas: aqui o backend ainda NAO e container - ele
# roda pelo mvnw no host (FABIANO-12 a 19 mudam isso). Por isso as opcoes de
# aplicacao chamam os scripts de infra/ em vez de mexer em container.
#
# NOTAS DE POWERSHELL: arquivo em ASCII puro (o PS 5.1 le UTF-8 sem BOM como
# ANSI e embaralha acento); ErrorActionPreference = Continue, porque com Stop
# qualquer coisa que um comando nativo escreva em stderr vira erro fatal.
# =============================================================================

$ErrorActionPreference = "Continue"

$compose = "docker-compose.dev.yml"

function Rodar {
    param([string[]]$Argumentos)
    Push-Location $PSScriptRoot
    docker compose -f $compose @Argumentos
    Pop-Location
}

function Titulo($t) { Write-Host ""; Write-Host $t -ForegroundColor Cyan }

# Sobe apenas se o Docker respondeu; sem isso o erro vem em cascata e confunde.
function DockerNoAr {
    docker version --format "{{.Server.Version}}" 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  Docker nao respondeu. O Docker Desktop esta aberto?" -ForegroundColor Red
        return $false
    }
    return $true
}

function ContainerNoAr($nome) {
    $achou = (docker ps --filter "name=$nome" --format "{{.Names}}" 2>&1) -join ""
    return ($achou -match [regex]::Escape($nome))
}

# Abre so o que esta realmente no ar, para nao encher o navegador de aba com erro.
function AbrirLinks {
    Start-Process "http://localhost:8080/swagger-ui.html"
    if (ContainerNoAr "fabiano-dozzle")     { Start-Process "http://localhost:9999" }
    if (ContainerNoAr "fabiano-grafana")    { Start-Process "http://localhost:3000" }
    if (ContainerNoAr "fabiano-prometheus") { Start-Process "http://localhost:9091/targets" }
}

function LogsServico {
    Write-Host ""
    Write-Host "  Servicos: mysql, dozzle, prometheus, loki, promtail, grafana" -ForegroundColor DarkGray
    $servico = Read-Host "  Qual servico? (enter = todos)"
    if ([string]::IsNullOrWhiteSpace($servico)) { Rodar "logs", "-f" }
    else { Rodar "logs", "-f", $servico }
}

# Remove o .git/*.lock quando fica preso por um Ctrl+C no meio de um commit
# ("Unable to create index.lock: File exists"). So apaga se NAO houver git.exe
# rodando: se houver, e um commit de verdade em andamento e mexer no lock
# corrompe o index. O teste e global e conservador de proposito.
function LimparGitLock {
    Write-Host ""
    if (Get-Process git -ErrorAction SilentlyContinue) {
        Write-Host "  Tem git.exe rodando agora - NAO vou apagar lock nenhum." -ForegroundColor Red
        Write-Host "  Espere terminar e tente de novo." -ForegroundColor Red
        return
    }
    $pastaGit = Join-Path $PSScriptRoot ".git"
    if (-not (Test-Path $pastaGit)) {
        Write-Host "  Pasta .git nao encontrada." -ForegroundColor DarkGray
        return
    }
    $locks = Get-ChildItem $pastaGit -Filter "*.lock" -ErrorAction SilentlyContinue
    if (-not $locks) {
        Write-Host "  Nenhum lock encontrado - nada para limpar." -ForegroundColor Green
        return
    }
    foreach ($lock in $locks) {
        Remove-Item $lock.FullName -Force
        Write-Host ("  removido: " + $lock.Name) -ForegroundColor Yellow
    }
    Write-Host "  Pode rodar o git de novo." -ForegroundColor Green
}

function Acessos {
    Write-Host ""
    Write-Host "Acessos locais:" -ForegroundColor Yellow
    Write-Host "  Swagger          : http://localhost:8080/swagger-ui.html"
    Write-Host "  Health           : http://localhost:8080/actuator/health"
    Write-Host "  Dozzle (logs)    : http://localhost:9999"
    Write-Host "  Grafana          : http://localhost:3000        (admin / admin)"
    Write-Host "  Prometheus       : http://localhost:9091/targets"
    Write-Host "  Loki             : http://localhost:3100"
    Write-Host "  MySQL 8.4        : localhost:3307   (root / root)"
    Write-Host "  MySQL migracao   : localhost:3308   (perfil migracao)"
    Write-Host ""
}

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "   Fabiano - ambiente de desenvolvimento"        -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  AMBIENTE" -ForegroundColor White
Write-Host "  1  subir tudo         (banco + observabilidade + abre os links)" -ForegroundColor Green
Write-Host "  2  subir so o banco   (MySQL 8.4 + Dozzle)"
Write-Host "  3  status             (containers no ar)"
Write-Host "  4  logs               (escolhe o servico)"
Write-Host "  5  derrubar           (mantem os dados)"
Write-Host "  6  derrubar e APAGAR  (remove os volumes - perde o banco local)" -ForegroundColor DarkYellow
Write-Host ""
Write-Host "  APLICACAO" -ForegroundColor White
Write-Host "  7  rodar a aplicacao  (restaura o dump de producao antes)" -ForegroundColor Green
Write-Host "  8  rodar sem restaurar(usa o banco local como esta)"
Write-Host ""
Write-Host "  TESTES" -ForegroundColor White
Write-Host "  9  smoke test         (precisa da aplicacao no ar em outra janela)"
Write-Host " 10  migrations do zero (sobe um 8.4 descartavel na 3308)"
Write-Host " 11  ensaio do cliente  (troca do MySQL client em container)"
Write-Host ""
Write-Host "  UTIL" -ForegroundColor White
Write-Host " 12  abrir os links no navegador"
Write-Host " 13  corrigir lock do git (index.lock preso)" -ForegroundColor DarkGray
Write-Host ""
$op = Read-Host "Escolha uma opcao"

switch ($op) {

    "1" {
        if (-not (DockerNoAr)) { break }
        Titulo "Subindo banco + observabilidade..."
        Rodar "--profile", "observabilidade", "up", "-d"
        Start-Sleep -Seconds 3
        Write-Host ""
        Write-Host "  A stack esta no ar. A APLICACAO ainda nao - use a opcao 7 ou 8" -ForegroundColor Yellow
        Write-Host "  numa outra janela, senao o Prometheus nao tem o que raspar." -ForegroundColor Yellow
        AbrirLinks
    }

    "2" {
        if (-not (DockerNoAr)) { break }
        Titulo "Subindo MySQL 8.4 e Dozzle..."
        Rodar "up", "-d"
    }

    "3" { Rodar "--profile", "observabilidade", "--profile", "migracao", "ps" }

    "4" { LogsServico }

    "5" {
        Titulo "Derrubando (os dados ficam)..."
        Rodar "--profile", "observabilidade", "--profile", "migracao", "down"
    }

    "6" {
        Write-Host ""
        Write-Host "  Isto APAGA o banco local, as metricas e os logs guardados." -ForegroundColor Red
        $confirma = Read-Host "  Digite APAGAR para confirmar"
        if ($confirma -ceq "APAGAR") {
            Rodar "--profile", "observabilidade", "--profile", "migracao", "down", "-v"
            Write-Host "  Removido." -ForegroundColor Green
        } else {
            Write-Host "  Cancelado." -ForegroundColor DarkGray
        }
    }

    "7"  { Push-Location $PSScriptRoot; & .\infra\dev-local.ps1; Pop-Location }
    "8"  { Push-Location $PSScriptRoot; & .\infra\dev-local.ps1 -SemRestaurar; Pop-Location }
    "9"  { Push-Location $PSScriptRoot; & .\infra\smoke-local.ps1; Pop-Location }
    "10" { Push-Location $PSScriptRoot; & .\infra\testar-mysql84.ps1; Pop-Location }
    "11" { Push-Location $PSScriptRoot; & .\infra\ensaio-cliente84.ps1; Pop-Location }

    "12" { AbrirLinks }
    "13" { LimparGitLock }

    default { Write-Host "Opcao invalida." -ForegroundColor Red }
}

Acessos
Read-Host "Pressione Enter para fechar"
