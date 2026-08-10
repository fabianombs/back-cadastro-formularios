#!/usr/bin/env bash
# =============================================================================
# aplicar-cors-hml.sh                                        (FABIANO-50)
#
# Aplica o CORS com curinga da Vercel no .env da homolog QUE JA ESTA NO AR, e
# recria o backend para valer.
#
# ISTO E TEMPORARIO POR NATUREZA. A homolog e recriada do zero pela esteira, e
# o .env dela e escrito pelo infra/subir-homolog.sh. Quem garante o valor no
# proximo ciclo e a linha corrigida naquele script — esta aqui so evita esperar
# 20 minutos de recriacao para provar o preview hoje.
#
# SO RODA EM HOMOLOGACAO.
# =============================================================================
set -uo pipefail

DIR_DEPLOY="${DIR_DEPLOY:-/home/ec2-user/fabiano/deploy}"
VALOR='https://hml.nexventa.com.br,https://*.vercel.app'

cd "$DIR_DEPLOY" || { echo "ERRO: $DIR_DEPLOY nao existe"; exit 1; }
env_get() { grep -E "^${1}=" .env 2>/dev/null | head -1 | cut -d= -f2-; }

echo "maquina: $(hostname)   $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M:%S')"

# A guarda mais importante deste script. Este curinga em producao deixaria
# qualquer projeto Vercel do mundo chamar a API autenticada pelo navegador de um
# usuario logado.
if ! env_get DB_HOST | grep -q 'homolog'; then
  echo "ABORTADO: esta maquina NAO e a de homologacao."
  echo "          O curinga '*.vercel.app' nunca pode entrar em producao."
  exit 1
fi

echo "antes:  CORS_ALLOWED_ORIGINS=$(env_get CORS_ALLOWED_ORIGINS)"

# sed com | como separador: o valor tem barras. E o '*' e literal na
# substituicao do sed, nao precisa de escape.
if grep -q '^CORS_ALLOWED_ORIGINS=' .env; then
  sed -i "s|^CORS_ALLOWED_ORIGINS=.*|CORS_ALLOWED_ORIGINS=${VALOR}|" .env
else
  echo "CORS_ALLOWED_ORIGINS=${VALOR}" >> .env
fi

DEPOIS=$(env_get CORS_ALLOWED_ORIGINS)
echo "depois: CORS_ALLOWED_ORIGINS=${DEPOIS}"

# Verificar depois de transformar: um sed que nao casa nada sai com 0 e deixa o
# arquivo intacto.
if [ "$DEPOIS" != "$VALOR" ]; then
  echo "ERRO: o .env nao ficou com o valor esperado. Nada foi recriado."
  exit 1
fi

echo
echo ">>> recriando o backend para ler o .env novo"
docker compose up -d --no-deps --force-recreate --pull never backend >/dev/null 2>&1
for i in $(seq 1 150); do
  E=$(docker inspect --format='{{.State.Health.Status}}' fabiano-backend 2>/dev/null || echo ausente)
  [ "$E" = healthy ] && break
  sleep 1
done
echo "    backend: $(docker inspect --format='{{.State.Health.Status}}' fabiano-backend 2>/dev/null)"

# Prova pelo protocolo, nao pela configuracao: um preflight com Origin de
# preview da Vercel tem que voltar com o Access-Control-Allow-Origin ecoando
# aquela origem. Se voltar sem o header, o navegador barraria o login.
echo
echo ">>> preflight de /auth/login com Origin de preview da Vercel:"
curl -s -i -X OPTIONS --max-time 20 \
  --resolve api-hml.nexventa.com.br:443:127.0.0.1 \
  -H 'Origin: https://app-forms-clients-git-teste-preview.vercel.app' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type' \
  https://api-hml.nexventa.com.br/auth/login \
  | grep -iE '^HTTP/|^access-control-' | sed 's/^/    /'

echo
echo "Esperado: HTTP 200 e um 'access-control-allow-origin' ecoando a origem."
echo "Se nao vier o header, o navegador barraria o login do preview."
