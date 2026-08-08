# =============================================================================
# Valida deploy/nginx/nginx.conf sem encostar em producao (FABIANO-15)
#
# Sobe um container nginx descartavel, monta a config real e roda 'nginx -t'.
#
# O que este teste PROVA:
#   - sintaxe de toda diretiva
#   - que o include do proxy-common.conf resolve
#   - que o bloco upstream esta bem formado
#   - que a imagem nginx:1.28-alpine existe no registry
#
# O que este teste NAO prova:
#   - que os caminhos de /etc/letsencrypt existem na EC2 (aqui sao falsos)
#   - que o proxy alcanca o backend (nao ha backend de pe)
# Essas duas so na EC2, depois do FABIANO-13.
#
# Uso:  .\infra\testar-nginx-conf.ps1
# =============================================================================

$ErrorActionPreference = "Stop"

$raiz    = Split-Path -Parent $PSScriptRoot
$config  = Join-Path $raiz "deploy\nginx"
$stub    = Join-Path $env:TEMP "fabiano-nginx-stub"
$dominio = "100-30-35-83.sslip.io"

if (-not (Test-Path (Join-Path $config "nginx.conf"))) {
    Write-Host "Nao achei deploy/nginx/nginx.conf" -ForegroundColor Red
    exit 1
}

Write-Host "==> [1/3] Gerando /etc/letsencrypt falso em $stub" -ForegroundColor Cyan

if (Test-Path $stub) { Remove-Item -Recurse -Force $stub }
New-Item -ItemType Directory -Force -Path (Join-Path $stub "live\$dominio") | Out-Null

# Comando de UMA linha de proposito. Um here-string do PowerShell entrega o
# texto com CRLF, e o 'sh' do Alpine trata o \r como parte do argumento -
# '>/dev/null\r' vira "redir error". Sem quebra de linha nao existe \r.
#
# O nginx CARREGA cert, chave e dhparam durante o 'nginx -t': arquivo vazio nao
# serve, tem que ser PEM valido. Por isso a geracao acontece de verdade.
# dhparam com -dsaparam: 2048 bits, porque o OpenSSL 3 do nginx 1.28 recusa DH
# menor que isso com "dh key too small". O -dsaparam gera em segundos em vez de
# minutos; o parametro resultante e mais fraco, o que aqui nao importa - ele e
# apagado no fim do script e nunca chega perto de producao.
$gerar = "apk add --no-cache openssl >/dev/null 2>&1; " +
         "openssl req -x509 -newkey rsa:2048 -nodes -days 1 " +
         "-keyout /out/live/$dominio/privkey.pem " +
         "-out /out/live/$dominio/fullchain.pem -subj '/CN=$dominio' >/dev/null 2>&1; " +
         "openssl dhparam -dsaparam -out /out/ssl-dhparams.pem 2048 >/dev/null 2>&1; " +
         "printf 'ssl_protocols TLSv1.2 TLSv1.3;\nssl_prefer_server_ciphers off;\n' > /out/options-ssl-nginx.conf; " +
         "chmod -R a+r /out; ls -1 /out /out/live/$dominio"

docker run --rm -v "${stub}:/out" alpine:3.20 sh -c $gerar
if ($LASTEXITCODE -ne 0) {
    Write-Host "Falhou ao gerar os arquivos falsos" -ForegroundColor Red
    exit 1
}

Write-Host "==> [2/3] Rodando nginx -t na config real" -ForegroundColor Cyan

# --add-host backend: o nginx resolve o nome do upstream na hora de carregar a
# config, e sem isso o teste falharia com "host not found in upstream" - o que
# seria um falso negativo, ja que na EC2 quem resolve 'backend' e o DNS interno
# do compose.
docker run --rm `
    --add-host "backend:127.0.0.1" `
    -v "${config}\nginx.conf:/etc/nginx/conf.d/default.conf:ro" `
    -v "${config}\proxy-common.conf:/etc/nginx/proxy-common.conf:ro" `
    -v "${stub}:/etc/letsencrypt:ro" `
    nginx:1.28-alpine nginx -t

$resultado = $LASTEXITCODE

Write-Host "==> [3/3] Limpando" -ForegroundColor Cyan
Remove-Item -Recurse -Force $stub

if ($resultado -eq 0) {
    Write-Host "`nCONFIG VALIDA" -ForegroundColor Green
} else {
    Write-Host "`nCONFIG INVALIDA - nao subir na EC2" -ForegroundColor Red
}
exit $resultado
