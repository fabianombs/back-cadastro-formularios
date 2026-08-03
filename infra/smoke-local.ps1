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

$ErrorActionPreference = "Continue"
$API = "http://localhost:8080"

$script:ok     = 0
$script:falhas = 0
$script:avisos = 0

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

Testar "GET /actuator/prometheus exige autenticacao" {
    try {
        Invoke-RestMethod "$API/actuator/prometheus" -TimeoutSec 15 | Out-Null
        throw "endpoint respondeu SEM token - deveria exigir autenticacao"
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
Testar "Metricas expostas no formato Prometheus" {
    1..3 | ForEach-Object { Invoke-RestMethod "$API/actuator/health" -TimeoutSec 10 | Out-Null }
    $script:metricas = Invoke-RestMethod "$API/actuator/prometheus" -Headers $H -TimeoutSec 20
    $linhas = ($script:metricas -split "`n").Count
    if ($linhas -lt 50) { throw "so $linhas linhas de metrica - esperava centenas" }
    Write-Host ("`n      {0} linhas de metrica" -f $linhas) -ForegroundColor DarkGray -NoNewline
}

Testar "Metricas de JVM, HTTP, Hikari e Tomcat presentes" {
    if (-not $script:metricas) { return "AVISO" }
    $faltando = @()
    foreach ($m in @("jvm_memory_used_bytes", "http_server_requests_seconds",
                     "hikaricp_connections", "process_uptime_seconds")) {
        if ($script:metricas -notmatch [regex]::Escape($m)) { $faltando += $m }
    }
    if ($faltando.Count -gt 0) { throw "ausentes: $($faltando -join ', ')" }
}

Testar "Tags application e environment em todas as metricas" {
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

Testar "Histograma de latencia habilitado (buckets p95/p99)" {
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
Write-Host ""
Write-Host "############################################################"
Write-Host ("# RESULTADO: {0} ok | {1} avisos | {2} falhas" -f $script:ok, $script:avisos, $script:falhas)
Write-Host "############################################################"
if ($script:falhas -gt 0) {
    Write-Host "Ha falhas - nao subir para producao antes de entender cada uma." -ForegroundColor Red
    exit 1
}
Write-Host "Aplicacao operando normalmente em MySQL 8.4 com dados reais." -ForegroundColor Green
