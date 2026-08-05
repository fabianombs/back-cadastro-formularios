# =============================================================================
# Smoke test do ambiente local em MySQL 8.4 (FABIANO-7)
# =============================================================================
# Exercita os fluxos da aplicacao contra o banco restaurado de producao.
# Nao altera producao. Escreve poucos registros de teste no banco LOCAL.
#
# Pre-requisito: .\infra\dev-local.ps1 rodando em outra janela (app na 8080).
#
# Uso:  .\infra\smoke-local.ps1
#
# NOTAS DE POWERSHELL: ASCII puro (PS 5.1 le UTF-8 sem BOM como ANSI);
# ErrorActionPreference = Continue; nada de barra invertida escapando aspas.
# =============================================================================

param(
    # Alvo do smoke. Local por padrao; a maquina nova e alcancada apontando o
    # dominio de producao para o IP dela no arquivo hosts do Windows, para que
    # o certificado continue casando.
    [string]$Api = "http://localhost:8080"
)

$ErrorActionPreference = "Continue"

# TRAVA: este smoke ESCREVE no banco - cria usuario, formulario e submissao.
# Rodar contra producao sujaria o banco do cliente com lixo de teste.
#
# A checagem e por IP RESOLVIDO, nao pelo texto da URL, justamente porque o
# jeito de apontar para a maquina nova e reescrever a resolucao do mesmo nome.
# GetHostAddresses le o arquivo hosts, entao a trava enxerga o que o HTTP vai
# enxergar de fato.
$IP_PRODUCAO = "100.30.35.83"
try {
    $alvoHost = ([uri]$Api).Host
    $ips = [System.Net.Dns]::GetHostAddresses($alvoHost) | ForEach-Object { $_.IPAddressToString }
} catch {
    Write-Host ("ABORTADO: nao consegui resolver {0}" -f $Api) -ForegroundColor Red
    exit 1
}
if ($ips -contains $IP_PRODUCAO) {
    Write-Host ""
    Write-Host ("ABORTADO: {0} resolve para {1} - isso e PRODUCAO." -f $Api, $IP_PRODUCAO) -ForegroundColor Red
    Write-Host "Este smoke grava no banco. Aponte para a maquina de ensaio." -ForegroundColor Red
    exit 1
}
Write-Host ("Alvo: {0}  ->  {1}" -f $Api, ($ips -join ", ")) -ForegroundColor Cyan

$script:ok      = 0
$script:falhas  = 0
$script:avisos  = 0
$script:pulados = 0

# Contra localhost a aplicacao responde direto. Contra a maquina remota o
# trafego atravessa o nginx, e o nginx bloqueia /actuator por design (so
# /health passa). Alem disso o log em JSON fica DENTRO do container, fora do
# alcance de um script rodando no Windows.
#
# Sem distinguir os dois casos, o smoke remoto acusa 11 falhas que nao existem
# - e pior: a secao de log lia um arquivo VELHO da maquina local e concluia
# coisas sobre ele. Teste que le a fonte errada e mais perigoso que teste que
# falha, porque um dia ele passa.
$MODO_REMOTO = ([uri]$Api).Host -notin @("localhost", "127.0.0.1")

function Testar($nome, $bloco) {
    Write-Host ("  {0,-52} " -f $nome) -NoNewline
    try {
        $r = & $bloco
        if ($r -eq "AVISO") { Write-Host "AVISO" -ForegroundColor Yellow; $script:avisos++ }
        else { Write-Host "ok" -ForegroundColor Green; $script:ok++ }
    } catch {
        Write-Host "FALHOU" -ForegroundColor Red
        Write-Host ("      -> {0}" -f $_.Exception.Message) -ForegroundColor DarkRed
        $script:falhas++
    }
}

# Verificacao que so faz sentido contra a aplicacao direta. PULADO aparece na
# tela e no resumo de proposito: teste que sumiu do relatorio vira teste que
# ninguem sente falta.
function TestarLocal($nome, $bloco) {
    if ($MODO_REMOTO) {
        Write-Host ("  {0,-52} " -f $nome) -NoNewline
        Write-Host "PULADO (so local)" -ForegroundColor DarkGray
        $script:pulados++
    } else {
        Testar $nome $bloco
    }
}

function StatusDoErro($err) {
    try {
        $resp = $err.Exception.Response
        if ($null -eq $resp) { return 0 }
        return [int]$resp.StatusCode
    } catch { return 0 }
}

function Secao($t) { Write-Host ""; Write-Host $t -ForegroundColor Cyan }

Write-Host "############################################################"
Write-Host "# SMOKE TEST - aplicacao em MySQL 8.4 com dados de producao"
Write-Host "############################################################"

# -----------------------------------------------------------------------------
Secao "[1] Aplicacao no ar"
# -----------------------------------------------------------------------------
Testar "GET /actuator/health responde UP" {
    $r = Invoke-RestMethod "$API/actuator/health" -TimeoutSec 10
    if ($r.status -ne "UP") { throw "status=$($r.status)" }
}

# -----------------------------------------------------------------------------
Secao "[2] Autenticacao"
# -----------------------------------------------------------------------------
# Usuario novo a cada rodada: /auth/register e publico e devolve o JWT direto,
# entao nao precisamos da senha de ninguem do dump.
$usuario = "smoke_" + (Get-Random -Minimum 10000 -Maximum 99999)
$token = $null

Testar "POST /auth/register cria ADMIN e devolve JWT" {
    $body = @{
        name            = "Smoke Test"
        email           = "$usuario@teste.local"
        username        = $usuario
        password        = "Smoke@12345"
        confirmPassword = "Smoke@12345"
    } | ConvertTo-Json
    $r = Invoke-RestMethod "$API/auth/register" -Method Post -Body $body -ContentType "application/json"
    if (-not $r.token) { throw "sem token na resposta" }
    $script:token = $r.token
}

Testar "POST /auth/login com a senha certa devolve JWT" {
    $body = @{ username = $usuario; password = "Smoke@12345" } | ConvertTo-Json
    $r = Invoke-RestMethod "$API/auth/login" -Method Post -Body $body -ContentType "application/json"
    if (-not $r.token) { throw "sem token" }
    $script:token = $r.token
}

# Caminho negativo: um teste que so verifica o caminho feliz nao prova nada.
Testar "POST /auth/login com senha errada e RECUSADO" {
    $recusou = $false
    try {
        $body = @{ username = $usuario; password = "senha-errada" } | ConvertTo-Json
        Invoke-RestMethod "$API/auth/login" -Method Post -Body $body -ContentType "application/json" | Out-Null
    } catch { $recusou = $true }
    if (-not $recusou) { throw "aceitou senha errada" }
}

$token = $script:token
if (-not $token) {
    Write-Host ""
    Write-Host "Sem token - os testes autenticados nao podem rodar. Parando." -ForegroundColor Red
    exit 1
}
$H = @{ Authorization = "Bearer $token" }

# -----------------------------------------------------------------------------
Secao "[3] Leitura dos dados de producao"
# -----------------------------------------------------------------------------
$templates = $null

Testar "GET /form-templates lista os templates" {
    $r = Invoke-RestMethod "$API/form-templates" -Headers $H
    # Spring devolve Page<T>: os itens ficam em .content, nao na raiz
    $script:templates = $r.content
    if (-not $script:templates) { throw "lista vazia - o dump nao foi restaurado?" }
}

Testar "GET /dashboard como ADMIN (agregacoes SQL)" {
    $r = Invoke-RestMethod "$API/dashboard?page=0&size=20" -Headers $H
    if ($null -eq $r) { throw "resposta vazia" }
}

Testar "GET /clients lista clientes" {
    Invoke-RestMethod "$API/clients" -Headers $H | Out-Null
}

$templates = $script:templates
$tpl   = $templates | Select-Object -First 1
$tplId = $tpl.id
$slug  = $tpl.slug

# Os endpoints de agenda recusam (400) template sem hasSchedule, e esse e o
# comportamento correto. Entao procuramos um que realmente tenha agenda.
$tplAgenda   = $templates | Where-Object { $_.hasSchedule } | Select-Object -First 1
$tplAgendaId = $tplAgenda.id

# Para presenca, escolhe o template com MAIS registros: o primeiro da lista
# tinha 6 linhas, e medir desempenho em 6 linhas nao prova nada.
$tplPresenca = $null
$maiorTotal  = -1
foreach ($cand in ($templates | Where-Object { $_.hasAttendance })) {
    try {
        $r = Invoke-RestMethod "$API/attendance/template/$($cand.id)?page=0&size=1" -TimeoutSec 20
        if ($r.totalElements -gt $maiorTotal) { $maiorTotal = $r.totalElements; $tplPresenca = $cand }
    } catch { }
}
if (-not $tplPresenca) { $tplPresenca = $tpl; $maiorTotal = 0 }
$tplPresencaId = $tplPresenca.id

Write-Host ("      templates: geral=id{0} ('{1}')" -f $tplId, $slug) -ForegroundColor DarkGray
Write-Host ("                 presenca=id{0} ({1} registros) | agenda={2}" -f $tplPresencaId, $maiorTotal,
    $(if ($tplAgendaId) { "id$tplAgendaId" } else { "NENHUM no dump" })) -ForegroundColor DarkGray

# -----------------------------------------------------------------------------
Secao "[4] Formulario publico (sem autenticacao)"
# -----------------------------------------------------------------------------
Testar "GET /form-templates/slug/{slug} sem token" {
    if (-not $slug) { return "AVISO" }
    $r = Invoke-RestMethod "$API/form-templates/slug/$slug"
    if (-not $r.id) { throw "sem id na resposta" }
}

Testar "POST /form-submissions grava resposta" {
    if (-not $tplId) { return "AVISO" }
    $body = @{ templateId = $tplId; values = @{ "Nome" = "Smoke Test"; "CPF" = "00000000191" } } | ConvertTo-Json
    Invoke-RestMethod "$API/form-submissions" -Method Post -Body $body -ContentType "application/json" | Out-Null
}

# -----------------------------------------------------------------------------
Secao "[5] Presenca (a maior tabela: 8919 + 4342 registros)"
# -----------------------------------------------------------------------------
$registros = $null

Testar "GET /attendance/template/{id} pagina a lista" {
    $script:registros = Invoke-RestMethod "$API/attendance/template/$tplPresencaId" -TimeoutSec 30
}

Testar "GET /attendance/template/existence" {
    Invoke-RestMethod "$API/attendance/template/existence?templateIds=$tplPresencaId" | Out-Null
}

Testar "PATCH /attendance/{id}/mark grava filled_at (V60)" {
    $registros = $script:registros
    $rec = $null
    if ($registros.content) { $rec = $registros.content | Select-Object -First 1 }
    elseif ($registros -is [array]) { $rec = $registros | Select-Object -First 1 }
    if (-not $rec) { return "AVISO" }
    $body = @{ attended = $true; notes = "smoke test"; companionsCount = $null } | ConvertTo-Json
    Invoke-RestMethod "$API/attendance/$($rec.id)/mark" -Method Patch -Body $body -ContentType "application/json" | Out-Null
}

# -----------------------------------------------------------------------------
Secao "[6] Agendamento (lock pessimista e deduplicacao)"
# -----------------------------------------------------------------------------
$amanha = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")

Testar "GET /slots em template COM agenda" {
    if (-not $tplAgendaId) { return "AVISO" }
    $r = Invoke-RestMethod "$API/appointments/template/$tplAgendaId/slots?date=$amanha"
    if ($null -eq $r.slots) { throw "resposta sem a lista de slots" }
}

Testar "GET /slots/range em template COM agenda" {
    if (-not $tplAgendaId) { return "AVISO" }
    $ate = (Get-Date).AddDays(7).ToString("yyyy-MM-dd")
    Invoke-RestMethod "$API/appointments/template/$tplAgendaId/slots/range?from=$amanha&to=$ate" | Out-Null
}

# Caminho negativo: template SEM agenda tem que ser recusado.
Testar "GET /slots em template SEM agenda e RECUSADO" {
    $semAgenda = $templates | Where-Object { -not $_.hasSchedule } | Select-Object -First 1
    if (-not $semAgenda) { return "AVISO" }
    $recusou = $false
    try { Invoke-RestMethod "$API/appointments/template/$($semAgenda.id)/slots?date=$amanha" | Out-Null }
    catch { $recusou = $true }
    if (-not $recusou) { throw "aceitou consulta de agenda em template sem agenda" }
}

Testar "GET /appointments/template/{id} lista agendamentos" {
    Invoke-RestMethod "$API/appointments/template/$tplId" | Out-Null
}

# -----------------------------------------------------------------------------
Secao "[7] Equipamentos e quiz"
# -----------------------------------------------------------------------------
Testar "GET /equipment/template/{id}/catalogs" {
    Invoke-RestMethod "$API/equipment/template/$tplId/catalogs" -Headers $H | Out-Null
}

Testar "GET /quizzes lista" {
    Invoke-RestMethod "$API/quizzes" -Headers $H | Out-Null
}

# -----------------------------------------------------------------------------
Secao "[8] Seguranca"
# -----------------------------------------------------------------------------
Testar "GET /form-templates SEM token e RECUSADO" {
    $recusou = $false
    $status = 0
    try { Invoke-RestMethod "$API/form-templates" | Out-Null } catch { $status = StatusDoErro $_ }
    if ($status -eq 0) { throw "endpoint protegido respondeu sem token" }
    # 401 exato, nao so "deu erro": o projeto tem authenticationEntryPoint
    # proprio porque o padrao do Spring Security 6 e 403, e o interceptor do
    # Angular so redireciona para o login quando recebe 401.
    if ($status -ne 401) { throw "esperava 401, veio $status - o interceptor do front nao trataria" }
}


# -----------------------------------------------------------------------------
Secao "[9] Regras de negocio: deduplicacao e capacidade de slot"
# -----------------------------------------------------------------------------
# Estes testes MUDAM a configuracao de agenda do template para criar um cenario
# controlado, e restauram no fim. So faca isso em base local descartavel.
$agendaOriginal = $null
$criados = @()

if (-not $tplAgendaId) {
    Write-Host "  (nenhum template com agenda no dump - secao pulada)" -ForegroundColor Yellow
} else {
    Testar "Preparar cenario: capacidade 2, dedup por CPF" {
        $tplCompleto = Invoke-RestMethod "$API/form-templates/slug/$($tplAgenda.slug)"
        $script:agendaOriginal = $tplCompleto.scheduleConfig
        $cfg = @{
            startTime           = "08:00:00"
            endTime             = "18:00:00"
            slotDurationMinutes = 30
            maxDaysAhead        = 30
            slotCapacity        = 2
            dedupFields         = @("CPF")
        } | ConvertTo-Json
        Invoke-RestMethod "$API/form-templates/$tplAgendaId/schedule-config" -Method Patch `
            -Body $cfg -ContentType "application/json" -Headers $H | Out-Null
    }

    # Data bem a frente para nao colidir com agendamento real do dump
    $dataTeste = (Get-Date).AddDays(20).ToString("yyyy-MM-dd")
    $horaTeste = "14:00:00"
    $cpfA = "11122233344"
    $cpfB = "55566677788"
    $cpfC = "99900011122"

    function Agendar($cpf, $nome) {
        $b = @{
            templateId      = $tplAgendaId
            slotDate        = $dataTeste
            slotTime        = $horaTeste
            bookedByName    = $nome
            bookedByContact = "teste@local"
            extraValues     = @{ "CPF" = $cpf }
        } | ConvertTo-Json
        Invoke-RestMethod "$API/appointments/book" -Method Post -Body $b -ContentType "application/json"
    }

    Testar "1o agendamento (CPF A) e aceito" {
        $r = Agendar $cpfA "Teste A"
        if (-not $r.id) { throw "sem id no retorno" }
        $script:criados += $r.id
    }

    Testar "2o agendamento com o MESMO CPF e recusado (409)" {
        $st = 0
        try { Agendar $cpfA "Teste A de novo" | Out-Null }
        catch { $st = StatusDoErro $_ }
        if ($st -ne 409) { throw "esperava 409 (DuplicateBooking), veio $st" }
    }

    Testar "2o agendamento com CPF diferente e aceito (lota o slot)" {
        $r = Agendar $cpfB "Teste B"
        $script:criados += $r.id
    }

    Testar "3o agendamento no slot cheio e recusado (409 SlotFull)" {
        $st = 0
        try { Agendar $cpfC "Teste C" | Out-Null }
        catch { $st = StatusDoErro $_ }
        if ($st -ne 409) { throw "esperava 409 (SlotFull), veio $st" }
    }

    Testar "Slot aparece como indisponivel apos lotar" {
        $r = Invoke-RestMethod "$API/appointments/template/$tplAgendaId/slots?date=$dataTeste"
        $slot = $r.slots | Where-Object { $_.time -like "14:00*" } | Select-Object -First 1
        if (-not $slot) { throw "slot 14:00 nao encontrado" }
        if ($slot.available) { throw "slot ainda marcado como disponivel com $($slot.bookedCount)/$($slot.capacity)" }
    }

    Testar "Cancelar libera a vaga" {
        if ($script:criados.Count -lt 1) { return "AVISO" }
        Invoke-RestMethod "$API/appointments/$($script:criados[0])/cancel" -Method Patch -Headers $H | Out-Null
        $r = Invoke-RestMethod "$API/appointments/template/$tplAgendaId/slots?date=$dataTeste"
        $slot = $r.slots | Where-Object { $_.time -like "14:00*" } | Select-Object -First 1
        if (-not $slot.available) { throw "slot continua lotado apos cancelamento" }
    }

    Testar "Limpeza: cancelar o restante e restaurar a agenda" {
        foreach ($id in $script:criados) {
            try { Invoke-RestMethod "$API/appointments/$id/cancel" -Method Patch -Headers $H | Out-Null } catch { }
        }
        if ($script:agendaOriginal) {
            $cfg = $script:agendaOriginal | ConvertTo-Json
            Invoke-RestMethod "$API/form-templates/$tplAgendaId/schedule-config" -Method Patch `
                -Body $cfg -ContentType "application/json" -Headers $H | Out-Null
        }
    }
}

# -----------------------------------------------------------------------------
Secao "[10] Coluna Preenchido em (filled_at, V60)"
# -----------------------------------------------------------------------------
# Usa a lista que ja existe no dump. Nao importa nada: o import SUBSTITUI a
# lista inteira e destruiria os 4342 registros restaurados.
Testar "Registro importado tem filledAt vazio" {
    $r = Invoke-RestMethod "$API/attendance/template/$tplPresencaId`?page=0&size=50"
    $semFilled = $r.content | Where-Object { -not $_.filledAt } | Select-Object -First 1
    if (-not $semFilled) { return "AVISO" }
    $script:recSemFilled = $semFilled.id
    $script:rowDataOrig  = $semFilled.rowData
}

# O contrato tem dois campos com significados distintos:
#   attendedAt = quando a pessoa foi marcada como presente
#   filledAt   = quando a LINHA da planilha foi preenchida/editada
# Marcar presenca NAO pode mexer no filledAt.
Testar "Marcar presenca grava attendedAt e NAO mexe em filledAt" {
    if (-not $script:recSemFilled) { return "AVISO" }
    $b = @{ attended = $true; notes = "teste"; companionsCount = $null } | ConvertTo-Json
    $r = Invoke-RestMethod "$API/attendance/$($script:recSemFilled)/mark" -Method Patch `
            -Body $b -ContentType "application/json"
    if (-not $r.attendedAt) { throw "attendedAt vazio apos marcar presenca" }
    if ($r.filledAt) { throw "marcar presenca gravou filledAt (deveria ser so na edicao da linha)" }
}

Testar "Editar a linha grava filledAt (V60)" {
    if (-not $script:recSemFilled) { return "AVISO" }
    # Reenvia o mesmo conteudo: o objetivo e disparar updateRowData sem alterar dado
    $b = ($script:rowDataOrig | ConvertTo-Json)
    if (-not $script:rowDataOrig) { $b = '{"Nome":"Teste"}' }
    $r = Invoke-RestMethod "$API/attendance/$($script:recSemFilled)/data" -Method Patch `
            -Body $b -ContentType "application/json"
    if (-not $r.filledAt) { throw "filledAt continua vazio apos editar a linha" }
}

# -----------------------------------------------------------------------------
Secao "[11] Desempenho da lista de presenca em MySQL 8.4"
# -----------------------------------------------------------------------------
# A tabela attendance_record_data tem ~8919 linhas e e a consulta mais pesada
# do sistema. Se o 8.4 degradasse algum plano de execucao, apareceria aqui.
Testar "Carregar ate 500 registros abaixo de 5s" {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $r = Invoke-RestMethod "$API/attendance/template/$tplPresencaId`?page=0&size=500" -TimeoutSec 60
    $sw.Stop()
    $ms = $sw.ElapsedMilliseconds
    Write-Host ("`n      {0} registros em {1} ms" -f $r.content.Count, $ms) -ForegroundColor DarkGray -NoNewline
    if ($ms -gt 5000) { throw "demorou ${ms}ms (limite 5000)" }
}

Testar "Contadores da lista batem com o total" {
    $r = Invoke-RestMethod "$API/attendance/template/$tplPresencaId`?page=0&size=1"
    if ($r.totalElements -lt 1) { throw "totalElements=$($r.totalElements)" }
    Write-Host ("`n      total na lista: {0}" -f $r.totalElements) -ForegroundColor DarkGray -NoNewline
}

# -----------------------------------------------------------------------------
Secao "[12] Observabilidade - metricas do Micrometer (FABIANO-22)"
# -----------------------------------------------------------------------------
# Propriedade do Spring escrita errado e IGNORADA EM SILENCIO: nada falha, a
# metrica simplesmente nao aparece, e a gente so descobre no dia que precisar
# do p95. Por isso cada item aqui confere o conteudo da resposta, nao so o 200.

# Do lado de fora, o certo NAO e 401 - e nao existir. O nginx devolve 404 para
# todo /actuator menos /health, entao um 401 aqui significaria que o bloqueio
# caiu e a superficie de ataque aumentou sem ninguem notar.
#
# O Prometheus nao e afetado: ele coleta em backend:8080, dentro da rede do
# compose, sem passar pelo nginx.
if ($MODO_REMOTO) {
    Testar "Pelo nginx, /actuator/prometheus NAO existe (404)" {
        try {
            Invoke-WebRequest "$API/actuator/prometheus" -UseBasicParsing -TimeoutSec 10 | Out-Null
            throw "respondeu 200 - o bloqueio do nginx caiu"
        } catch {
            $s = StatusDoErro $_
            if ($s -ne 404) { throw "esperava 404, veio $s" }
        }
    }
    Testar "Pelo nginx, /actuator/env tambem NAO existe (404)" {
        try {
            Invoke-WebRequest "$API/actuator/env" -UseBasicParsing -TimeoutSec 10 | Out-Null
            throw "respondeu 200 - vazamento de configuracao"
        } catch {
            $s = StatusDoErro $_
            if ($s -ne 404) { throw "esperava 404, veio $s" }
        }
    }
}

TestarLocal "GET /actuator/prometheus exige autenticacao" {
    try {
        Invoke-RestMethod "$API/actuator/prometheus" -TimeoutSec 15 | Out-Null
        throw "endpoint respondeu SEM token - deveria exigir autenticacao"
    } catch {
        $st = StatusDoErro $_
        if ($st -ne 401 -and $st -ne 403) { throw "esperava 401/403, veio $st" }
    }
}

# O Prometheus nao usa JWT: token de coletor nao pode expirar em 24h.
$TOKEN_METRICAS = "token-local-de-desenvolvimento-nao-use-em-producao"

TestarLocal "Token de coleta libera /actuator/prometheus (FABIANO-23)" {
    $r = Invoke-RestMethod "$API/actuator/prometheus" -TimeoutSec 20 `
            -Headers @{ "X-Metrics-Token" = $TOKEN_METRICAS }
    if (("$r" -split "`n").Count -lt 50) { throw "resposta curta demais para ser a saida de metricas" }
}

TestarLocal "Prometheus consegue pelo header Authorization" {
    # Forma que o proprio Prometheus envia. O tipo NAO e Bearer de proposito,
    # para o filtro JWT nao tentar interpretar o valor como token.
    $r = Invoke-RestMethod "$API/actuator/prometheus" -TimeoutSec 20 `
            -Headers @{ Authorization = "Metrics-Token $TOKEN_METRICAS" }
    if ("$r" -notmatch "jvm_memory_used_bytes") { throw "veio resposta, mas sem as metricas" }
}

TestarLocal "Token de coleta errado e RECUSADO" {
    try {
        Invoke-RestMethod "$API/actuator/prometheus" -TimeoutSec 15 `
            -Headers @{ "X-Metrics-Token" = "token-errado" } | Out-Null
        throw "token errado foi aceito"
    } catch {
        $st = StatusDoErro $_
        if ($st -ne 401 -and $st -ne 403) { throw "esperava 401/403, veio $st" }
    }
}

Testar "GET /actuator/health continua publico" {
    $r = Invoke-RestMethod "$API/actuator/health" -TimeoutSec 10
    if ($r.status -ne "UP") { throw "status=$($r.status)" }
}

# Gera trafego antes de ler as metricas: sem requisicao registrada o medidor
# http_server_requests nem existe, e o teste passaria por engano.
TestarLocal "Metricas expostas no formato Prometheus" {
    1..3 | ForEach-Object { Invoke-RestMethod "$API/actuator/health" -TimeoutSec 10 | Out-Null }
    $script:metricas = Invoke-RestMethod "$API/actuator/prometheus" -Headers $H -TimeoutSec 20
    $linhas = ($script:metricas -split "`n").Count
    if ($linhas -lt 50) { throw "so $linhas linhas de metrica - esperava centenas" }
    Write-Host ("`n      {0} linhas de metrica" -f $linhas) -ForegroundColor DarkGray -NoNewline
}

TestarLocal "Metricas de JVM, HTTP, Hikari e Tomcat presentes" {
    if (-not $script:metricas) { return "AVISO" }
    $faltando = @()
    foreach ($m in @("jvm_memory_used_bytes", "http_server_requests_seconds",
                     "hikaricp_connections", "process_uptime_seconds")) {
        if ($script:metricas -notmatch [regex]::Escape($m)) { $faltando += $m }
    }
    if ($faltando.Count -gt 0) { throw "ausentes: $($faltando -join ', ')" }
}

TestarLocal "Tags application e environment em todas as metricas" {
    if (-not $script:metricas) { return "AVISO" }
    if ($script:metricas -notmatch 'application="fabiano-back"') {
        throw "tag application ausente - o MeterRegistryCustomizer nao aplicou"
    }
    if ($script:metricas -notmatch 'environment="dev"') {
        throw "tag environment ausente ou diferente de dev"
    }
    # Uma metrica de JVM tem que carregar a tag tambem: e justamente o que a
    # propriedade management.observations.key-values NAO alcancaria.
    $jvm = ($script:metricas -split "`n") | Where-Object { $_ -match "^jvm_memory_used_bytes" } | Select-Object -First 1
    if ($jvm -and $jvm -notmatch 'application="fabiano-back"') {
        throw "metrica de JVM sem a tag application"
    }
}

TestarLocal "Contadores de login expostos (FABIANO-24)" {
    if (-not $script:metricas) { return "AVISO" }
    # A secao [2] ja exercitou login com senha certa e com senha errada, entao
    # os dois resultados tem que estar registrados. Se o contador nao aparecer,
    # a instrumentacao do AuthService nao chegou ao registry.
    if ($script:metricas -notmatch "auth_login_total") {
        throw "auth_login_total ausente - AuthService nao instrumentado?"
    }
    foreach ($r in @("sucesso", "falha_credenciais")) {
        if ($script:metricas -notmatch ('resultado="' + $r + '"')) {
            throw "sem amostra com resultado=$r"
        }
    }
}

TestarLocal "Nenhum dado pessoal virou rotulo de metrica" {
    if (-not $script:metricas) { return "AVISO" }
    # Identificador de pessoa como rotulo e dado pessoal no monitoramento e
    # cardinalidade sem teto: mil usuarios virariam mil series temporais.
    $linhasAuth = ($script:metricas -split "`n") | Where-Object { $_ -match "^auth_" }
    foreach ($proibido in @("username=", "email=", "cpf=", "usuario=")) {
        $vazou = $linhasAuth | Where-Object { $_ -match [regex]::Escape($proibido) }
        if ($vazou) { throw "rotulo proibido nas metricas de auth: $proibido" }
    }
    Write-Host ("`n      {0} linhas auth_* conferidas" -f $linhasAuth.Count) -ForegroundColor DarkGray -NoNewline
}

TestarLocal "Histograma de latencia habilitado (buckets p95/p99)" {
    if (-not $script:metricas) { return "AVISO" }
    # O bucket so existe com percentiles-histogram ligado. Se a propriedade
    # tivesse sido ignorada, viriam apenas _count e _sum.
    if ($script:metricas -notmatch "http_server_requests_seconds_bucket") {
        throw "sem _bucket - percentiles-histogram nao pegou (propriedade ignorada?)"
    }
    $achados = [regex]::Matches($script:metricas, 'http_server_requests_seconds_bucket\{[^}]*le="([^"]+)"')
    $les = @($achados | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
    Write-Host ("`n      {0} limites de bucket" -f $les.Count) -ForegroundColor DarkGray -NoNewline
    if ($les.Count -lt 5) { throw "so $($les.Count) limites - esperava os SLOs configurados" }
}

# -----------------------------------------------------------------------------
Secao "[13] Correlacao de requisicao (FABIANO-26)"
# -----------------------------------------------------------------------------
# Invoke-WebRequest, e nao Invoke-RestMethod: so ele expoe os cabecalhos da
# resposta. -UseBasicParsing evita a dependencia do motor do Internet Explorer
# no PowerShell 5.1.

function CabecalhoDaResposta($resp, $nome) {
    if (-not $resp.Headers.ContainsKey($nome)) { return $null }
    return (@($resp.Headers[$nome]) -join "")
}

Testar "Resposta traz o header X-Request-Id" {
    $r = Invoke-WebRequest "$API/actuator/health" -UseBasicParsing -TimeoutSec 10
    $id = CabecalhoDaResposta $r "X-Request-Id"
    if (-not $id) { throw "header ausente - o RequestIdFilter nao esta na cadeia" }
    if ($id.Length -lt 8) { throw "identificador suspeito: '$id'" }
    Write-Host ("`n      {0}" -f $id) -ForegroundColor DarkGray -NoNewline
}

Testar "Cada requisicao recebe um identificador diferente" {
    $a = CabecalhoDaResposta (Invoke-WebRequest "$API/actuator/health" -UseBasicParsing) "X-Request-Id"
    $b = CabecalhoDaResposta (Invoke-WebRequest "$API/actuator/health" -UseBasicParsing) "X-Request-Id"
    if ($a -eq $b) { throw "duas requisicoes com o mesmo id: $a" }
}

Testar "Identificador enviado pelo cliente e reaproveitado" {
    # Permite rastrear a mesma chamada atravessando sistemas.
    $meu = "teste-correlacao-12345"
    $r = Invoke-WebRequest "$API/actuator/health" -UseBasicParsing `
            -Headers @{ "X-Request-Id" = $meu }
    $id = CabecalhoDaResposta $r "X-Request-Id"
    if ($id -ne $meu) { throw "esperava '$meu', veio '$id'" }
}

Testar "Identificador malformado do cliente e descartado" {
    # O valor vem de fora: sem filtro, daria para injetar conteudo forjado no
    # log ou mandar um valor gigante em toda requisicao.
    # Sem quebra de linha no valor: o proprio cliente HTTP recusaria antes de
    # sair da maquina, e o teste nao provaria nada sobre o filtro. O que se
    # testa aqui e espaco, simbolo e comprimento.
    $sujo = "valor invalido com espacos, (parenteses) e comprimento " + ("x" * 200)
    $r = Invoke-WebRequest "$API/actuator/health" -UseBasicParsing `
            -Headers @{ "X-Request-Id" = $sujo }
    $id = CabecalhoDaResposta $r "X-Request-Id"
    if ($id -eq $sujo) { throw "o valor sujo do cliente foi aceito" }
    if ($id.Length -gt 64) { throw "identificador com $($id.Length) caracteres" }
    if ($id -notmatch '^[A-Za-z0-9_-]+$') { throw "identificador com caractere fora do permitido: '$id'" }
}

# -----------------------------------------------------------------------------
Secao "[14] Log de acesso (FABIANO-24)"
# -----------------------------------------------------------------------------
# O arquivo JSON so existe no perfil dev (logging.structured.format.file=ecs).
# E ele que o Promtail le e entrega ao Loki - conferir aqui e conferir a ponta
# de entrada da cadeia inteira de log.
$ArquivoLog = Join-Path (Split-Path $PSScriptRoot -Parent) "logs\app.json"

function LinhasDoLog($quantas = 600) {
    if (-not (Test-Path $ArquivoLog)) { throw "log nao encontrado em $ArquivoLog" }
    $objetos = @()
    foreach ($linha in (Get-Content $ArquivoLog -Tail $quantas -Encoding UTF8)) {
        if (-not $linha.Trim()) { continue }
        # Linha malformada nao derruba o teste: o que importa e achar a que
        # procuramos, e uma linha truncada no fim do arquivo e normal.
        try { $objetos += ($linha | ConvertFrom-Json) } catch { }
    }
    return $objetos
}

TestarLocal "Requisicao gera linha de acesso com o mesmo requestId" {
    $meu = "smoke-acesso-" + (Get-Random -Maximum 999999)
    Invoke-WebRequest "$API/form-templates" -UseBasicParsing -TimeoutSec 20 `
        -Headers ($H + @{ "X-Request-Id" = $meu }) | Out-Null
    Start-Sleep -Milliseconds 900

    $linhas = @(LinhasDoLog)
    $acesso = @($linhas | Where-Object { $_.log.logger -eq "acesso" })
    Write-Host ("`n      {0} linhas lidas, {1} do logger acesso" -f $linhas.Count, $acesso.Count) -ForegroundColor DarkGray -NoNewline
    if ($acesso.Count -eq 0) { throw "nenhuma linha do logger 'acesso' - o AccessLogFilter nao esta na cadeia" }

    $minha = @($acesso | Where-Object { $_.requestId -eq $meu })
    if ($minha.Count -eq 0) { throw "nenhuma linha de acesso com requestId '$meu'" }

    $l = $minha[-1]
    if (-not $l.status)     { throw "linha sem o campo status" }
    if (-not $l.duracaoMs)  { throw "linha sem o campo duracaoMs" }
    if ($l.message -notmatch "GET /form-templates") { throw "mensagem inesperada: $($l.message)" }
    Write-Host ("`n      {0}" -f $l.message) -ForegroundColor DarkGray -NoNewline
}

TestarLocal "O /actuator fica fora do log de acesso" {
    # Sem esta exclusao, o scrape do Prometheus a cada 15 segundos viraria mais
    # de quatro linhas por minuto, para sempre, no disco da t2.micro.
    $marca = "smoke-actuator-" + (Get-Random -Maximum 999999)
    Invoke-WebRequest "$API/actuator/health" -UseBasicParsing -TimeoutSec 10 `
        -Headers @{ "X-Request-Id" = $marca } | Out-Null
    Start-Sleep -Milliseconds 900

    $poluicao = @(LinhasDoLog | Where-Object { $_.log.logger -eq "acesso" -and $_.requestId -eq $marca })
    if ($poluicao.Count -gt 0) { throw "o /actuator gerou $($poluicao.Count) linha(s) de acesso" }
    Write-Host "`n      nenhuma linha de acesso para /actuator" -ForegroundColor DarkGray -NoNewline
}

# -----------------------------------------------------------------------------
Secao "[15] Metricas de negocio (FABIANO-25)"
# -----------------------------------------------------------------------------
# O card exige "cada metrica validada com uma acao real na aplicacao". Conferir
# que o nome aparece no /actuator/prometheus NAO prova isso: um contador criado
# e nunca incrementado apareceria igual. Entao aqui se le o valor ANTES, se faz
# a acao, e se le DEPOIS - o teste passa so se o numero mexeu.

function LerMetricas {
    $r = Invoke-WebRequest "$API/actuator/prometheus" -UseBasicParsing -TimeoutSec 20 `
            -Headers @{ "X-Metrics-Token" = $TOKEN_METRICAS }
    return $r.Content
}

function ValorDe($texto, $nome, $rotulos) {
    # Formato do Prometheus: nome{rotulo="valor",...} 3.0
    # Contador que ainda nao foi incrementado nem aparece na saida: por isso
    # ausencia devolve 0, e nao erro - o que interessa e a diferenca.
    foreach ($linha in ($texto -split "`n")) {
        $linha = $linha.Trim()
        if (-not $linha.StartsWith($nome + "{")) { continue }
        $combina = $true
        foreach ($r in $rotulos) {
            if ($linha -notlike "*$r*") { $combina = $false; break }
        }
        if (-not $combina) { continue }
        $partes = $linha -split "\s+"
        return [double]$partes[-1]
    }
    return 0.0
}

TestarLocal "Submissao bem-sucedida incrementa formulario_submissao_total" {
    if (-not $tplId) { return "AVISO" }
    $antes = ValorDe (LerMetricas) "formulario_submissao_total" @('resultado="sucesso"')

    $body = @{ templateId = $tplId; values = @{ "Nome" = "Metrica Negocio" } } | ConvertTo-Json
    Invoke-RestMethod "$API/form-submissions" -Method Post -Body $body -ContentType "application/json" | Out-Null

    $depois = ValorDe (LerMetricas) "formulario_submissao_total" @('resultado="sucesso"')
    Write-Host ("`n      {0} -> {1}" -f $antes, $depois) -ForegroundColor DarkGray -NoNewline
    if ($depois -le $antes) { throw "contador nao subiu (antes=$antes depois=$depois)" }
}

TestarLocal "Submissao com template inexistente conta erro e erro_tratado" {
    $antesErro  = ValorDe (LerMetricas) "formulario_submissao_total" @('resultado="erro"')
    $antesTrata = ValorDe (LerMetricas) "erro_tratado_total" @('status="400"')

    $body = @{ templateId = 999999999; values = @{ "Nome" = "Inexistente" } } | ConvertTo-Json
    try {
        Invoke-RestMethod "$API/form-submissions" -Method Post -Body $body -ContentType "application/json" | Out-Null
        throw "template inexistente foi aceito"
    } catch {
        $st = StatusDoErro $_
        if ($st -ne 400) { throw "esperava 400, veio $st" }
    }

    $depoisErro  = ValorDe (LerMetricas) "formulario_submissao_total" @('resultado="erro"')
    $depoisTrata = ValorDe (LerMetricas) "erro_tratado_total" @('status="400"')
    Write-Host ("`n      submissao erro {0} -> {1} | erro tratado {2} -> {3}" -f
        $antesErro, $depoisErro, $antesTrata, $depoisTrata) -ForegroundColor DarkGray -NoNewline
    if ($depoisErro -le $antesErro)   { throw "formulario_submissao_total{resultado=erro} nao subiu" }
    if ($depoisTrata -le $antesTrata) { throw "erro_tratado_total{status=400} nao subiu" }
}

function EnviarArquivoMultipart($url, $caminho, $tipo, $cabecalhos) {
    # O PowerShell 5.1 nao tem o parametro -Form do Invoke-RestMethod: o corpo
    # multipart e montado a mao. A conversao por iso-8859-1 preserva os bytes
    # um a um na ida e na volta - com UTF-8 o conteudo binario se corromperia.
    $limite = [System.Guid]::NewGuid().ToString()
    $bytes  = [System.IO.File]::ReadAllBytes($caminho)
    $texto  = [System.Text.Encoding]::GetEncoding("iso-8859-1").GetString($bytes)
    $nome   = [System.IO.Path]::GetFileName($caminho)
    $corpo  = (
        "--$limite",
        "Content-Disposition: form-data; name=`"file`"; filename=`"$nome`"",
        "Content-Type: $tipo",
        "",
        $texto,
        "--$limite--",
        ""
    ) -join "`r`n"
    return Invoke-RestMethod $url -Method Post -TimeoutSec 20 -Headers $cabecalhos `
        -ContentType "multipart/form-data; boundary=$limite" -Body $corpo
}

TestarLocal "Upload invalido incrementa upload_imagem_total{resultado=erro}" {
    $antes = ValorDe (LerMetricas) "upload_imagem_total" @('resultado="erro"')

    # Arquivo de texto anunciado como imagem: o validador recusa pelo tipo.
    $temp = Join-Path $env:TEMP "smoke-imagem-invalida.txt"
    Set-Content -Path $temp -Value "isto nao e uma imagem" -Encoding ASCII
    try {
        EnviarArquivoMultipart "$API/uploads/image" $temp "text/plain" $H | Out-Null
    } catch { }
    Remove-Item $temp -ErrorAction SilentlyContinue

    $depois = ValorDe (LerMetricas) "upload_imagem_total" @('resultado="erro"')
    Write-Host ("`n      {0} -> {1}" -f $antes, $depois) -ForegroundColor DarkGray -NoNewline
    if ($depois -le $antes) {
        throw "contador nao subiu (antes=$antes depois=$depois) - o upload chegou a ser tentado?"
    }
}

TestarLocal "Nenhum Timer com sufixo de unidade escrito a mao" {
    # A pegadinha registrada no card: o Micrometer acrescenta _seconds sozinho.
    # Um timer chamado "duracao_segundos" viraria "duracao_segundos_seconds" e
    # a consulta do dashboard, escrita com o nome obvio, nao acharia nada.
    $texto = LerMetricas
    $ruins = @()
    foreach ($linha in ($texto -split "`n")) {
        if ($linha -match "^(agendamento|formulario|presenca|upload|erro)[a-z_]*_(segundos|ms|millis|bytes)_seconds") {
            $ruins += $linha.Split("{")[0]
        }
    }
    $ruins = @($ruins | Sort-Object -Unique)
    Write-Host ("`n      {0} metricas de negocio conferidas" -f
        @($texto -split "`n" | Where-Object { $_ -match "^(agendamento|formulario|presenca|upload|erro_tratado)" }).Count) -ForegroundColor DarkGray -NoNewline
    if ($ruins.Count -gt 0) { throw "nome com sufixo duplicado: $($ruins -join ', ')" }
}

TestarLocal "Nenhum rotulo de metrica de negocio carrega id ou dado pessoal" {
    # Regra de cardinalidade do card: rotulo so pode ser conjunto pequeno e
    # fechado. id de template, CPF ou e-mail criariam uma serie por valor.
    $texto = LerMetricas
    $suspeitas = @()
    $conferidas = 0
    foreach ($linha in ($texto -split "`n")) {
        if ($linha -notmatch "^(agendamento|formulario|presenca|upload|erro_tratado)") { continue }
        $conferidas++
        if ($linha -match '(templateId|template_id|clientId|client_id|cpf|email|slug|userId|user_id)=') {
            $suspeitas += $linha.Split(" ")[0]
        }
    }
    Write-Host ("`n      {0} linhas de metrica de negocio conferidas" -f $conferidas) -ForegroundColor DarkGray -NoNewline
    if ($conferidas -eq 0) { throw "nenhuma metrica de negocio encontrada - a instrumentacao subiu?" }
    if ($suspeitas.Count -gt 0) { throw "rotulo de alta cardinalidade: $($suspeitas -join ', ')" }
}

# -----------------------------------------------------------------------------
Write-Host ""
Write-Host "############################################################"
Write-Host ("# RESULTADO: {0} ok | {1} avisos | {2} falhas | {3} pulados" -f $script:ok, $script:avisos, $script:falhas, $script:pulados)
if ($script:pulados -gt 0) {
    Write-Host ("# {0} verificacoes so rodam contra localhost:8080 (nginx bloqueia /actuator," -f $script:pulados)
    Write-Host "#   e o log JSON fica dentro do container). Rode o smoke local tambem."
}
Write-Host "############################################################"
if ($script:falhas -gt 0) {
    Write-Host "Ha falhas - nao subir para producao antes de entender cada uma." -ForegroundColor Red
    exit 1
}
Write-Host "Aplicacao operando normalmente em MySQL 8.4 com dados reais." -ForegroundColor Green
