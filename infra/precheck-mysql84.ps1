# =============================================================================
# Precheck de compatibilidade MySQL 8.4 (FABIANO-5)
#
# Roda o 'util.checkForServerUpgrade' do MySQL Shell contra uma copia
# restaurada do banco - nunca contra producao.
#
# Por que tudo acontece dentro de um container:
#   - o RDS so aceita conexao do grupo de seguranca da EC2, entao e preciso
#     tunel SSH. Liberar o IP da maquina no SG de producao seria mais simples e
#     e justamente o tipo de mudanca que fica esquecida ligada.
#   - a EC2 esta com 99 MB de RAM livre e 2 GB de disco: instalar MySQL Shell
#     la seria mexer na maquina que atende o cliente.
#   - assim nada e instalado no Windows tambem.
#
# O tunel e o mysqlsh vivem no mesmo container e morrem com ele.
#
# Uso:
#   .\infra\precheck-mysql84.ps1 -Endpoint poc-fabiano-db-precheck.xxxx.us-east-1.rds.amazonaws.com
# =============================================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$Endpoint,

    [string]$Ec2      = "ec2-user@100.30.35.83",
    [string]$Chave    = "$env:USERPROFILE\.ssh\poc-fabiano",
    [string]$Usuario  = "admin",
    [string]$Alvo     = "8.4.10",
    [string]$Saida    = "precheck-8.4.json"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $Chave)) {
    Write-Host "Nao achei a chave SSH em $Chave" -ForegroundColor Red
    exit 1
}

$destino = Join-Path (Get-Location) $Saida
Write-Host "==> Endpoint : $Endpoint"
Write-Host "==> Tunel por: $Ec2"
Write-Host "==> Relatorio: $destino"
Write-Host ""

# Montado em /ssh como somente leitura. A chave e copiada para /tmp dentro do
# container porque o ssh recusa chave com permissao aberta, e nao da para
# ajustar permissao de arquivo vindo de bind mount do Windows.
$script = @(
    'set -e',
    'echo "instalando cliente ssh (leva ~1 min)..."',
    'microdnf install -y openssh-clients >/dev/null 2>&1',
    'echo "cliente ssh ok, abrindo tunel..."',
    'cp /ssh/chave /tmp/chave && chmod 600 /tmp/chave',
    # -f manda o ssh para segundo plano depois de estabelecer o tunel, -N nao
    # abre shell remoto. O tunel escuta em 127.0.0.1:3307 DENTRO do container.
    "ssh -i /tmp/chave -o StrictHostKeyChecking=no -o ExitOnForwardFailure=yes -f -N -L 3307:${Endpoint}:3306 $Ec2",
    'echo TUNEL-OK',
    "mysqlsh --user=$Usuario --host=127.0.0.1 --port=3307 --credential-store-helper='<disabled>' -- util check-for-server-upgrade --target-version=$Alvo --output-format=JSON > /out/$Saida"
) -join '; '

# O script vai codificado em base64 de proposito. Passar um comando com aspas
# duplas para um executavel externo faz o PowerShell quebrar a string em varios
# argumentos, e o 'bash -c' acaba executando so o primeiro pedaco - foi assim
# que a primeira versao imprimiu "instalando" e saiu. Em base64 o argumento e um
# token unico, sem espaco e sem aspas, e nao ha o que quebrar.
#
# O script e gravado em /tmp antes de rodar, e nao executado por pipe: um
# 'base64 -d | bash' consumiria o stdin do terminal e o mysqlsh nao conseguiria
# ler a senha nem o modo interativo.
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($script))

docker run --rm -it `
    -e LC_ALL=C `
    -v "${PWD}:/out" `
    -v "${Chave}:/ssh/chave:ro" `
    mysql:8.4 bash -c "echo $b64 | base64 -d > /tmp/passo.sh; bash /tmp/passo.sh"

$codigo = $LASTEXITCODE

if ((Test-Path $destino) -and ((Get-Item $destino).Length -gt 0)) {
    Write-Host "`nRelatorio gravado: $destino ($((Get-Item $destino).Length) bytes)" -ForegroundColor Green
} else {
    Write-Host "`nRelatorio vazio ou ausente - o precheck nao chegou a rodar" -ForegroundColor Red
}
exit $codigo
