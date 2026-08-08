# =============================================================================
# Ensaio da troca do cliente MySQL - reproduz a EC2 no Docker (FABIANO-8)
# =============================================================================
# Executa EXATAMENTE a mesma troca de pacotes que faremos na EC2, mas dentro
# de containers na sua maquina. Prova que nada quebra antes de encostar em
# producao.
#
# Espelhado da EC2 real:
#   amazonlinux:2           mesma imagem base (Amazon Linux 2 Karoo)
#   mariadb 5.5 + postfix   mesmo estado de pacotes, mesma dependencia critica
#   repo mysql-8.4-lts      mesma origem dos pacotes novos
#   mysql:8.0               mesma versao de servidor que o RDS roda hoje
#   root native_password    mesmo plugin do admin em producao - o ponto central
#
# Nao toca a AWS. Nao toca producao. Nao precisa de credencial.
#
# LIMITACAO CONHECIDA: o container reproduz o sistema operacional e os pacotes,
# mas NAO reproduz as restricoes do RDS. O root do container tem privilegios
# que o usuario master do RDS nao tem, e o RDS bloqueia operacoes que um MySQL
# comum permite - por exemplo FLUSH TABLES WITH READ LOCK, recusado com erro
# 1045 mesmo com RELOAD concedido. Foi assim que o dump passou aqui e falhou na
# EC2 em 03/08. Ensaio fiel no SO, infiel no banco gerenciado. So um ambiente
# restaurado de snapshot do RDS fecha essa lacuna (FABIANO-33).
#
# Uso:
#   .\infra\ensaio-cliente84.ps1
#   .\infra\ensaio-cliente84.ps1 -Derrubar
#
# REGRA DE ASPAS (aprendida errando duas vezes): string PowerShell com aspas
# SIMPLES por fora e DUPLAS por dentro chega picada no executavel nativo - o
# PowerShell nao escapa as duplas internas. Aqui e sempre duplas por fora e
# simples por dentro, e a senha vai por 'docker exec -e' para dispensar o
# sh -c na maioria dos comandos.
# =============================================================================

param(
    [string]$Dump = "C:\projetos\Fabiano\fabiano-real-20260802-1508.sql.gz",
    [switch]$Derrubar
)

$ErrorActionPreference = "Continue"

$REDE  = "ensaio84"
$C_DB  = "ensaio84-mysql80"
$C_OS  = "ensaio84-al2"
$BANCO = "fabiano_prod"

$script:erros = 0

function Titulo($m) {
    Write-Host ""
    Write-Host ("-" * 74) -ForegroundColor DarkGray
    Write-Host $m -ForegroundColor Cyan
    Write-Host ("-" * 74) -ForegroundColor DarkGray
}
function Ok($m)    { Write-Host ("  [ok]    " + $m) -ForegroundColor Green }
function Aviso($m) { Write-Host ("  [aviso] " + $m) -ForegroundColor Yellow }
function Erro($m)  { Write-Host ("  [FALHA] " + $m) -ForegroundColor Red; $script:erros++ }
function Parar($m) {
    Write-Host ""
    Write-Host ("ENSAIO INTERROMPIDO: " + $m) -ForegroundColor Red
    Write-Host "Para limpar:  .\infra\ensaio-cliente84.ps1 -Derrubar" -ForegroundColor Yellow
    exit 1
}
function Limpar {
    docker rm -f $C_DB $C_OS 2>&1 | Out-Null
    docker network rm $REDE 2>&1 | Out-Null
}

# Roda um comando no container do banco ja autenticado. Sem sh -c, sem aspas
# duplas no meio: cada argumento vai separado para o docker.
function SqlDb($consulta) {
    $r = docker exec -e MYSQL_PWD=root $C_DB mysql -uroot -N -B -e $consulta 2>&1
    return (($r | Where-Object { "$_" -notmatch "Warning" }) -join "`n").Trim()
}
function SqlDbOk($consulta) {
    docker exec -e MYSQL_PWD=root $C_DB mysql -uroot -N -B -e $consulta 2>&1 | Out-Null
    return ($LASTEXITCODE -eq 0)
}
function NoOs($cmd) {
    $r = docker exec $C_OS sh -c $cmd 2>&1
    return (($r) -join "`n").Trim()
}
function NumOs($cmd) {
    $t = NoOs $cmd
    if ("$t" -match "(\d+)") { return [int]$Matches[1] }
    return -1
}

if ($Derrubar) {
    Titulo "Removendo o ambiente de ensaio"
    Limpar
    Write-Host "Removido." -ForegroundColor Green
    exit 0
}

Write-Host ""
Write-Host "##############################################################" -ForegroundColor White
Write-Host "# ENSAIO DA TROCA DO CLIENTE MYSQL - AMBIENTE DESCARTAVEL" -ForegroundColor White
Write-Host "# Nada aqui alcanca a AWS nem producao." -ForegroundColor White
Write-Host "##############################################################" -ForegroundColor White

# -----------------------------------------------------------------------------
Titulo "[1/9] Preparando ambiente limpo"
# -----------------------------------------------------------------------------
docker version --format "{{.Server.Version}}" 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Parar "Docker nao respondeu. O Docker Desktop esta aberto?" }

if (-not (Test-Path $Dump)) {
    Parar ("Dump nao encontrado em '" + $Dump + "'. Passe o caminho com -Dump <arquivo>.")
}
Ok ("dump de producao: " + (Split-Path $Dump -Leaf) + " (" + [math]::Round((Get-Item $Dump).Length / 1KB, 0) + " KB)")

Limpar
docker network create $REDE 2>&1 | Out-Null
Ok "rede de ensaio criada"

# -----------------------------------------------------------------------------
Titulo "[2/9] Subindo MySQL 8.0 (mesma versao do RDS de producao)"
# -----------------------------------------------------------------------------
# --default-authentication-plugin=mysql_native_password reproduz a situacao
# real: em producao o usuario admin ainda usa esse plugin. E justamente o que
# precisamos provar que o cliente 8.4 continua conseguindo autenticar.
docker run -d --name $C_DB --network $REDE -e MYSQL_ROOT_PASSWORD=root -e "MYSQL_DATABASE=$BANCO" mysql:8.0 --default-authentication-plugin=mysql_native_password 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Parar "Falha ao subir o container do MySQL 8.0." }

Write-Host "  aguardando conexao autenticada..."
$pronto = $false
foreach ($i in 1..60) {
    $estado = (docker inspect --format "{{.State.Status}}" $C_DB 2>&1) -join ""
    if ($estado -notmatch "running") {
        Write-Host ""
        docker logs --tail 30 $C_DB 2>&1 | Write-Host
        Parar ("o container do MySQL nao esta rodando (estado: " + $estado + ")")
    }
    if (SqlDbOk "SELECT 1") { $pronto = $true; break }
    Start-Sleep -Seconds 2
    if ($i % 5 -eq 0) { Write-Host ("    ... " + ($i * 2) + "s") -ForegroundColor DarkGray }
}
if (-not $pronto) {
    Write-Host ""
    Write-Host "Ultima saida do cliente:" -ForegroundColor Yellow
    docker exec -e MYSQL_PWD=root $C_DB mysql -uroot -N -B -e "SELECT 1" 2>&1 | Write-Host
    Write-Host ""
    docker logs --tail 40 $C_DB 2>&1 | Write-Host
    Parar "MySQL nao aceitou conexao autenticada dentro de 120s (diagnostico acima)."
}

Ok ("servidor no ar: MySQL " + (SqlDb "SELECT VERSION()"))

# Sem WHERE com aspas aninhadas: traz tudo e filtra no PowerShell.
$linhaRoot = (SqlDb "SELECT user, host, plugin FROM mysql.user") -split "`n" |
    Where-Object { $_ -match "^root\s" } | Select-Object -First 1
if ("$linhaRoot" -match "native") {
    Ok ("root com mysql_native_password - mesmo cenario do admin em producao")
} else {
    Aviso ("plugin do root inesperado: " + $linhaRoot)
}

# -----------------------------------------------------------------------------
Titulo "[3/9] Restaurando o dump real de producao"
# -----------------------------------------------------------------------------
docker cp $Dump ($C_DB + ":/tmp/dump.sql.gz") 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Parar "Falha ao copiar o dump para o container." }

docker exec -e MYSQL_PWD=root $C_DB sh -c "gunzip -c /tmp/dump.sql.gz | mysql -uroot $BANCO" 2>&1 |
    Where-Object { "$_" -notmatch "Warning" } | Write-Host
if ($LASTEXITCODE -ne 0) { Parar "Falha ao restaurar o dump." }

$nTab = SqlDb "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()"
Ok ("tabelas no information_schema (todos os schemas do usuario): " + $nTab)
$nTabBanco = SqlDb ("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '" + $BANCO + "'")
Ok ("tabelas restauradas em " + $BANCO + ": " + $nTabBanco)
if ([int]$nTabBanco -lt 20) { Parar ("restauracao incompleta: so " + $nTabBanco + " tabelas") }

# -----------------------------------------------------------------------------
Titulo "[4/9] Subindo Amazon Linux 2 no estado exato da EC2"
# -----------------------------------------------------------------------------
docker run -d --name $C_OS --network $REDE amazonlinux:2 sleep infinity 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { Parar "Falha ao subir o container do Amazon Linux 2." }

Write-Host "  instalando mariadb 5.5 e postfix (o mesmo que existe na EC2)..."
docker exec $C_OS yum install -y mariadb postfix 2>&1 | Select-Object -Last 5 | Write-Host
if ($LASTEXITCODE -ne 0) { Parar "Falha ao instalar mariadb/postfix no container." }

$verAntes = (docker exec $C_OS mysql --version 2>&1) -join " "
Ok ("cliente antes: " + $verAntes)
if ($verAntes -notmatch "MariaDB") { Aviso "esperava cliente MariaDB aqui" }

Ok ("depende da libmysqlclient.so.18: " + (NoOs "rpm -q --whatrequires 'libmysqlclient.so.18()(64bit)'"))

# -----------------------------------------------------------------------------
Titulo "[5/9] Dump de referencia com o cliente MariaDB 5.5 (estado atual)"
# -----------------------------------------------------------------------------
docker exec -e MYSQL_PWD=root $C_OS sh -c "mysqldump -h $C_DB -uroot --single-transaction --set-gtid-purged=OFF --routines --triggers $BANCO > /tmp/antes.sql 2>/tmp/antes.err" 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    NoOs "cat /tmp/antes.err" | Write-Host
    Parar "O cliente MariaDB nao conseguiu gerar o dump. Ensaio invalido."
}

$bytesAntes = NumOs "stat -c%s /tmp/antes.sql"
$tabAntes   = NumOs "grep -c 'CREATE TABLE' /tmp/antes.sql"
$insAntes   = NumOs "grep -c '^INSERT INTO' /tmp/antes.sql"
$fimAntes   = NumOs "tail -5 /tmp/antes.sql | grep -c 'Dump completed'"
Ok ("referencia -> bytes=" + $bytesAntes + " tabelas=" + $tabAntes + " inserts=" + $insAntes + " marcador=" + $fimAntes)
if ($tabAntes -lt 1) { Parar "dump de referencia sem tabelas - ensaio invalido" }

# -----------------------------------------------------------------------------
Titulo "[6/9] Simulacao da transacao do yum (nao instala)"
# -----------------------------------------------------------------------------
docker exec $C_OS yum install -y https://dev.mysql.com/get/mysql84-community-release-el7-1.noarch.rpm 2>&1 | Select-Object -Last 3 | Write-Host
docker exec $C_OS rpm --import https://repo.mysql.com/RPM-GPG-KEY-mysql-2023 2>&1 | Out-Null

$sim = (docker exec $C_OS yum install --assumeno mysql-community-client mysql-community-libs mysql-community-libs-compat 2>&1) -join "`n"
Write-Host $sim

if ($sim -match "(?m)^\s*postfix\s") { Erro "o postfix aparece na transacao - NAO prosseguir na EC2" }
else { Ok "postfix fora da transacao" }

# -----------------------------------------------------------------------------
Titulo "[7/9] Instalando o cliente MySQL 8.4 (aqui sim, no container)"
# -----------------------------------------------------------------------------
docker exec $C_OS yum install -y mysql-community-client mysql-community-libs mysql-community-libs-compat 2>&1 |
    Select-Object -Last 12 | Write-Host
if ($LASTEXITCODE -ne 0) { Parar "A instalacao falhou dentro do container. NAO rodar na EC2." }

$verDepois = (docker exec $C_OS mysql --version 2>&1) -join " "
if ($verDepois -match "8\.4") { Ok ("cliente depois: " + $verDepois) }
else { Erro ("esperava cliente 8.4, veio: " + $verDepois) }

# -----------------------------------------------------------------------------
Titulo "[8/9] O postfix sobreviveu?"
# -----------------------------------------------------------------------------
$aindaTem = NoOs "rpm -q postfix"
if ($aindaTem -match "^postfix-") { Ok ("pacote intacto: " + $aindaTem) }
else { Erro ("postfix comprometido: " + $aindaTem) }

# Antes de confiar no laco, contar o que ele vai examinar. Laco cujo glob nao
# casa com nada nao acha problema nenhum e passa em silencio - foi exatamente
# assim que os backups vazios enganaram por meses (FABIANO-29).
$nBin = NumOs "ls -1 /usr/libexec/postfix/ | wc -l"
if ($nBin -lt 1) { Erro "nenhum binario do postfix encontrado - a verificacao abaixo nao valeria nada" }
else { Ok ("binarios do postfix a examinar: " + $nBin) }

# ldd em cada binario e modulo; "not found" = biblioteca ausente.
# $f escapado com crase para o shell resolver, nao o PowerShell.
$saidaLdd = NoOs "for f in /usr/libexec/postfix/* /usr/lib64/postfix/*.so; do [ -f `$f ] || continue; ldd `$f 2>/dev/null | grep -q 'not found' && echo QUEBRADO `$f; done; echo ---FIM---"
if ($saidaLdd -match "QUEBRADO") {
    Write-Host $saidaLdd
    Erro "ha binario do postfix com biblioteca ausente"
} elseif ($nBin -ge 1) {
    Ok ("nenhum dos " + $nBin + " binarios ficou com biblioteca ausente")
}

# Os pacotes da MySQL instalam em /usr/lib64/mysql/ e registram no ldconfig,
# nao em /usr/lib64/ direto. Perguntar ao carregador dinamico e mais confiavel
# que procurar num caminho fixo - foi o erro da versao anterior deste script.
$libCache = NoOs "ldconfig -p | grep 'libmysqlclient.so.18'"
if ($libCache -match "libmysqlclient.so.18") {
    Ok ("libmysqlclient.so.18 registrada: " + (($libCache -split "`n")[0] -replace "\s+", " ").Trim())
} else {
    Erro "libmysqlclient.so.18 nao esta registrada no ldconfig"
}

# Prova final e concreta: o binario do postfix que usa a biblioteca resolve
# o caminho de verdade, com endereco de carga.
$resolve = NoOs "ldd /usr/libexec/postfix/smtpd 2>/dev/null | grep mysql"
if ($resolve -match "=>\s*/") {
    Ok ("smtpd resolve -> " + ($resolve -replace "\s+", " ").Trim())
} else {
    Aviso ("smtpd nao referencia libmysqlclient (saida: " + $resolve + ")")
}

# -----------------------------------------------------------------------------
Titulo "[9/9] Dump com o cliente novo, contra o mesmo servidor 8.0"
# -----------------------------------------------------------------------------
docker exec -e MYSQL_PWD=root $C_OS sh -c "mysqldump -h $C_DB -uroot --single-transaction --set-gtid-purged=OFF --routines --triggers $BANCO > /tmp/depois.sql 2>/tmp/depois.err" 2>&1 | Out-Null
$rcDepois = $LASTEXITCODE

if ($rcDepois -ne 0) {
    NoOs "cat /tmp/depois.err" | Write-Host
    Erro "o cliente 8.4 NAO conseguiu gerar o dump (este e o teste central)"
} else {
    Ok "cliente 8.4 autenticou num usuario mysql_native_password e gerou o dump"

    $bytesDepois = NumOs "stat -c%s /tmp/depois.sql"
    $tabDepois   = NumOs "grep -c 'CREATE TABLE' /tmp/depois.sql"
    $insDepois   = NumOs "grep -c '^INSERT INTO' /tmp/depois.sql"
    $fimDepois   = NumOs "tail -5 /tmp/depois.sql | grep -c 'Dump completed'"

    Write-Host ""
    Write-Host ("  {0,-10} {1,14} {2,14}" -f "",         "MariaDB 5.5", "MySQL 8.4")
    Write-Host ("  {0,-10} {1,14} {2,14}" -f "bytes",    $bytesAntes,   $bytesDepois)
    Write-Host ("  {0,-10} {1,14} {2,14}" -f "tabelas",  $tabAntes,     $tabDepois)
    Write-Host ("  {0,-10} {1,14} {2,14}" -f "inserts",  $insAntes,     $insDepois)
    Write-Host ("  {0,-10} {1,14} {2,14}" -f "marcador", $fimAntes,     $fimDepois)
    Write-Host ""

    if ($fimDepois -lt 1) { Erro "dump do cliente 8.4 sem marcador 'Dump completed' - incompleto" }
    else { Ok "dump completo (marcador presente)" }

    if ($tabDepois -ne $tabAntes) { Erro ("tabelas divergiram: " + $tabAntes + " -> " + $tabDepois) }
    else { Ok ("mesma contagem de tabelas: " + $tabDepois) }

    if ($insDepois -ne $insAntes) { Erro ("inserts divergiram: " + $insAntes + " -> " + $insDepois) }
    else { Ok ("mesma contagem de inserts: " + $insDepois) }

    # Cabecalho e comentarios mudam entre versoes; o que nao pode e o volume
    # de dados encolher.
    if ($bytesAntes -gt 0) {
        $delta = [math]::Abs($bytesDepois - $bytesAntes) * 100.0 / $bytesAntes
        if ($delta -gt 5) { Erro ("tamanho variou " + [math]::Round($delta,1) + "% - investigar") }
        else { Ok ("tamanho equivalente (variacao de " + [math]::Round($delta,1) + "%)") }
    }
}

# -----------------------------------------------------------------------------
Write-Host ""
Write-Host ("=" * 74) -ForegroundColor DarkGray
if ($script:erros -eq 0) {
    Write-Host "  RESULTADO: 0 falhas." -ForegroundColor Green
    Write-Host "  A mesma troca pode ser executada na EC2 com seguranca." -ForegroundColor Green
} else {
    Write-Host ("  RESULTADO: " + $script:erros + " falha(s).") -ForegroundColor Red
    Write-Host "  NAO executar na EC2 ate entender o motivo." -ForegroundColor Red
}
Write-Host ("=" * 74) -ForegroundColor DarkGray
Write-Host ""
Write-Host "Para remover os containers de ensaio:" -ForegroundColor DarkGray
Write-Host "  .\infra\ensaio-cliente84.ps1 -Derrubar" -ForegroundColor DarkGray
Write-Host ""

if ($script:erros -gt 0) { exit 1 }
