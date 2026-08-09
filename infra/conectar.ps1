<#
.SYNOPSIS
    Configura os atalhos SSH das duas EC2 do projeto no ~/.ssh/config do Windows.

.DESCRIPTION
    Depois de rodar uma vez, conectar vira:

        ssh fabiano-nova      # producao, t3.medium, AL2023, Docker Compose
        ssh fabiano-antiga    # maquina legada, t2.micro, AL2, JAR + systemd

    O script e idempotente: rodar de novo nao duplica bloco nem sobrescreve
    configuracao de outros projetos.

.NOTES
    Nada aqui e segredo. A chave privada nunca e lida, copiada nem transmitida —
    o script so escreve o CAMINHO dela no config. O arquivo gerado fica em
    ~/.ssh/config, fora do repositorio.
#>

[CmdletBinding()]
param(
    # Elastic IP da producao. Parametrizado porque um EIP pode ser reassociado —
    # foi exatamente o que aconteceu na virada de 08/08/2026 (FABIANO-47).
    [string]$IpNova = '100.30.35.83',

    # A maquina antiga perdeu o IP publico quando o EIP migrou. So e alcancavel
    # pelo IP privado, saltando pela nova — dai o ProxyJump mais abaixo.
    [string]$IpAntiga = '172.31.28.215',

    [string]$Chave = '~/.ssh/poc-fabiano',

    [string]$Usuario = 'ec2-user'
)

$ErrorActionPreference = 'Stop'

$sshDir     = Join-Path $HOME '.ssh'
$configPath = Join-Path $sshDir 'config'

if (-not (Test-Path $sshDir)) {
    New-Item -ItemType Directory -Path $sshDir | Out-Null
    Write-Host "Criado $sshDir" -ForegroundColor Green
}

# Marcadores delimitam o trecho gerenciado por este script. Sem eles nao daria
# para reescrever o bloco sem arriscar apagar host de outro projeto.
$inicio = '# >>> fabiano-ssh (gerado por infra/conectar.ps1) >>>'
$fim    = '# <<< fabiano-ssh <<<'

$bloco = @"
$inicio
Host fabiano-nova
    HostName $IpNova
    User $Usuario
    IdentityFile $Chave
    ServerAliveInterval 60

Host fabiano-antiga
    HostName $IpAntiga
    User $Usuario
    IdentityFile $Chave
    ServerAliveInterval 60
    # ProxyJump em vez de agent forwarding (-A): com -A o socket do agente fica
    # exposto DENTRO da maquina do meio, e quem for root la consegue usar a
    # chave enquanto a sessao existir. Com ProxyJump a chave nunca sai do PC —
    # a maquina nova serve so de tunel de rede.
    ProxyJump fabiano-nova
$fim
"@

if (Test-Path $configPath) {
    $atual = Get-Content $configPath -Raw

    # Backup antes de qualquer escrita: config de SSH quebrado derruba o acesso
    # a tudo, nao so a este projeto.
    $backup = "$configPath.bak"
    Copy-Item $configPath $backup -Force
    Write-Host "Backup em $backup" -ForegroundColor DarkGray

    if ($atual -match [regex]::Escape($inicio)) {
        $padrao = "(?s)" + [regex]::Escape($inicio) + ".*?" + [regex]::Escape($fim)
        $novo = [regex]::Replace($atual, $padrao, $bloco.Replace('$', '$$'))
        Write-Host "Bloco existente atualizado." -ForegroundColor Yellow
    } else {
        $novo = $atual.TrimEnd() + "`n`n" + $bloco + "`n"
        Write-Host "Bloco adicionado ao config existente." -ForegroundColor Green
    }
    Set-Content -Path $configPath -Value $novo -Encoding utf8 -NoNewline
} else {
    Set-Content -Path $configPath -Value ($bloco + "`n") -Encoding utf8 -NoNewline
    Write-Host "Config criado." -ForegroundColor Green
}

# O OpenSSH do Windows recusa chave cujo arquivo seja legivel por outros
# usuarios. Herdar permissao da pasta do usuario e o caso mais comum, e o erro
# ('UNPROTECTED PRIVATE KEY FILE') so aparece na hora de conectar.
$chaveReal = $Chave -replace '^~', $HOME
if (Test-Path $chaveReal) {
    $acl = icacls $chaveReal 2>$null
    if ($acl -match 'BUILTIN|Todos|Everyone|Usuários|Users') {
        Write-Host "`nA chave esta com permissao aberta. Corrigindo..." -ForegroundColor Yellow
        icacls $chaveReal /inheritance:r          | Out-Null
        icacls $chaveReal /grant:r "$($env:USERNAME):(R)" | Out-Null
        Write-Host "Permissao ajustada." -ForegroundColor Green
    }
} else {
    Write-Warning "Chave nao encontrada em $chaveReal - ajuste o parametro -Chave."
}

Write-Host @"

Pronto. A partir de agora:

    ssh fabiano-nova        producao (Docker Compose, ~/fabiano/deploy)
    ssh fabiano-antiga      legada  (JAR + systemd, sem IP publico)

Teste rapido, sem abrir sessao:

    ssh -o BatchMode=yes fabiano-nova 'hostname; uptime'

"@ -ForegroundColor Cyan
