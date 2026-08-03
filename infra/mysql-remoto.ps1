# =============================================================================
# Roda SQL num RDS que so aceita conexao de dentro da VPC (FABIANO-5 em diante)
#
# O grupo de seguranca do RDS libera a porta 3306 apenas para o grupo da EC2 -
# nenhum IP externo entra. Em vez de abrir o SG (mudanca em producao que fica
# esquecida ligada), este script abre um tunel SSH pela EC2 de dentro de um
# container descartavel. Nada e instalado no Windows nem na EC2.
#
# Uso:
#   .\infra\mysql-remoto.ps1 -Endpoint <rds> -Sql "SELECT 1"
#   .\infra\mysql-remoto.ps1 -Endpoint <rds> -Arquivo consulta.sql
# =============================================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$Endpoint,

    [string]$Sql      = "",
    [string]$Arquivo  = "",

    # Abre uma sessao SQL interativa em vez de executar comando pronto. E o
    # unico jeito de rodar um ALTER USER sem a senha aparecer em argumento de
    # comando, em historico de shell ou em 'docker inspect'.
    [switch]$Interativo,
    [string]$Ec2      = "ec2-user@100.30.35.83",
    [string]$Chave    = "$env:USERPROFILE\.ssh\poc-fabiano",
    [string]$Usuario  = "admin"
)

$ErrorActionPreference = "Stop"

if (-not $Sql -and -not $Arquivo -and -not $Interativo) {
    Write-Host "Informe -Sql, -Arquivo ou -Interativo" -ForegroundColor Red
    exit 1
}
if (-not (Test-Path $Chave)) {
    Write-Host "Nao achei a chave SSH em $Chave" -ForegroundColor Red
    exit 1
}

if ($Interativo) {
    $comando = "mysqlsh --user=$Usuario --host=127.0.0.1 --port=3307 --credential-store-helper='<disabled>' --sql"
} elseif ($Arquivo) {
    $comando = "mysqlsh --user=$Usuario --host=127.0.0.1 --port=3307 --credential-store-helper='<disabled>' --sql --file=/out/$Arquivo"
} else {
    # As aspas duplas do SQL sao escapadas para sobreviverem ao bash -c.
    $sqlEscapado = $Sql.Replace('"','\"')
    $comando = "mysqlsh --user=$Usuario --host=127.0.0.1 --port=3307 --credential-store-helper='<disabled>' --sql -e `"$sqlEscapado`""
}

# A chave e copiada para /tmp porque o ssh recusa chave com permissao aberta, e
# arquivo vindo de bind mount do Windows aparece como 777 e nao da para ajustar.
$script = @(
    'set -e',
    'echo "instalando cliente ssh (leva ~1 min)..."',
    'microdnf install -y openssh-clients >/dev/null 2>&1',
    'echo "cliente ssh ok, abrindo tunel..."',
    'cp /ssh/chave /tmp/chave && chmod 600 /tmp/chave',
    "ssh -i /tmp/chave -o StrictHostKeyChecking=no -o ExitOnForwardFailure=yes -f -N -L 3307:${Endpoint}:3306 $Ec2",
    'echo TUNEL-OK',
    $comando
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

exit $LASTEXITCODE
