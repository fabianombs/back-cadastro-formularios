<#
.SYNOPSIS
    Configura os atalhos SSH das EC2 do projeto no ~/.ssh/config do Windows.

.DESCRIPTION
    Depois de rodar uma vez, conectar vira:

        ssh fabiano-nova      # producao, t3.medium, AL2023, Docker Compose
        ssh fabiano-antiga    # maquina legada, t2.micro, AL2, JAR + systemd
        ssh fabiano-hml       # homologacao sob demanda, efemera (FABIANO-33)

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

    # EIP fixo da homolog. A MAQUINA muda a cada ciclo; o endereco, nao.
    [string]$IpHomolog = '54.197.175.159',

    [string]$Chave = '~/.ssh/poc-fabiano',

    # Chave propria da homolog. Separada de proposito: quem tiver acesso a
    # homolog nao ganha acesso a producao junto.
    [string]$ChaveHomolog = '~/.ssh/poc-fabiano-homolog',

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

Host fabiano-hml
    HostName $IpHomolog
    User $Usuario
    IdentityFile $ChaveHomolog
    ServerAliveInterval 60
    # -------------------------------------------------------------------------
    # Por que esta maquina tem known_hosts SEPARADO
    # -------------------------------------------------------------------------
    # A homolog e destruida e recriada a cada ciclo, sempre com o mesmo Elastic
    # IP. Chave de host nova, endereco antigo: e exatamente a assinatura de um
    # ataque man-in-the-middle, e o SSH grita — corretamente.
    #
    # Se ela morasse no known_hosts normal, voce veria esse alerta toda semana e
    # aprenderia a passar por cima sem ler. Ai, no dia em que ele aparecesse na
    # PRODUCAO, voce passaria por cima tambem. O custo real nao e o incomodo: e
    # o treino de ignorar.
    #
    # Isolando num arquivo proprio, a frouxidao fica confinada na maquina
    # descartavel. 'accept-new' aceita host novo em silencio, mas continua
    # recusando se uma chave JA CONHECIDA mudar — nao e o mesmo que desligar a
    # verificacao.
    UserKnownHostsFile ~/.ssh/known_hosts_homolog
    StrictHostKeyChecking accept-new
    # Tunel do Mailpit: 'ssh -L 8025:localhost:8025 fabiano-hml' e depois abrir
    # http://localhost:8025. A caixa nao e exposta na internet de proposito.
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
foreach ($c in @($Chave, $ChaveHomolog)) {
    $chaveReal = $c -replace '^~', $HOME
    if (-not (Test-Path $chaveReal)) {
        Write-Warning "Chave nao encontrada em $chaveReal"
        continue
    }

    # $ErrorActionPreference = 'Stop' transforma QUALQUER escrita em stderr de um
    # programa externo em erro fatal — e o icacls escreve em stderr para coisas
    # que aqui nao sao erro. O caso real: 'Acesso negado' ao LER a ACL de uma
    # chave que ja esta trancada, ou seja, o estado que queremos. O script
    # abortava por ter encontrado exatamente o que procurava.
    #
    # A configuracao do SSH ja foi gravada acima; deixar isto derrubar o script
    # so faria parecer que nada funcionou.
    $eapAnterior = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $acl = icacls $chaveReal 2>&1 | Out-String
        if ($acl -match 'BUILTIN|Todos|Everyone|Usuários|Users') {
            Write-Host "`n$chaveReal esta com permissao aberta. Corrigindo..." -ForegroundColor Yellow
            icacls $chaveReal /inheritance:r                   2>&1 | Out-Null
            icacls $chaveReal /grant:r "$($env:USERNAME):(R)"  2>&1 | Out-Null
            Write-Host "Permissao ajustada." -ForegroundColor Green
        } elseif ($acl -match 'Acesso negado|Access is denied') {
            Write-Host "$chaveReal ja esta trancada (nem a leitura da ACL passa)." -ForegroundColor DarkGray
        }
    } finally {
        $ErrorActionPreference = $eapAnterior
    }
}

Write-Host @"

Pronto. A partir de agora:

    ssh fabiano-nova        producao (Docker Compose, ~/fabiano/deploy)
    ssh fabiano-antiga      legada  (JAR + systemd, sem IP publico)
    ssh fabiano-hml         homologacao efemera (morre em 24h sem uso)

Teste rapido, sem abrir sessao — repare no 'hostname':

    ssh -o BatchMode=yes fabiano-nova 'hostname; uptime'
    ssh -o BatchMode=yes fabiano-hml  'hostname; uptime'

O 'hostname' esta ai por um motivo. Producao e ip-172-31-12-104; homolog e
outra. Em 10/08/2026 um diagnostico inteiro quase foi lido na maquina errada,
e o que salvou foi o hostname no prompt.

"@ -ForegroundColor Cyan
