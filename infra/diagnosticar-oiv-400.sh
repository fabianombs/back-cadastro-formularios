#!/usr/bin/env bash
# =============================================================================
# diagnosticar-oiv-400.sh                                    (FABIANO-37)
#
# No ensaio de 10/08, com open-in-view=false, exatamente duas rotas passaram a
# devolver 400:
#
#   /form-templates/view/{viewToken}/submissions
#
# A hipotese e LazyInitializationException caindo no handler generico de
# RuntimeException, que responde 400. Este script NAO assume isso: ele reproduz
# a falha e imprime o que o log realmente diz.
#
# O ensaio anterior nao respondeu porque recriou o container ao restaurar, e o
# log foi junto.
#
# SO RODA EM HOMOLOGACAO. Recria o backend duas vezes.
# =============================================================================
set -uo pipefail

DIR_DEPLOY="${DIR_DEPLOY:-/home/ec2-user/fabiano/deploy}"
HOST_API="api-hml.nexventa.com.br"
BKP=".env.diag-oiv"

cd "$DIR_DEPLOY" || { echo "ERRO: $DIR_DEPLOY nao existe"; exit 1; }
env_get() { grep -E "^${1}=" .env 2>/dev/null | head -1 | cut -d= -f2-; }

echo "maquina: $(hostname)   $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M:%S')"
if ! env_get DB_HOST | grep -q 'homolog'; then
  echo "ABORTADO: nao e a maquina de homologacao."; exit 1
fi

DB_HOST=$(env_get DB_HOST); DB_NAME=$(env_get DB_NAME); DB_USER=$(env_get DB_USER)
DB_PORT=$(env_get DB_PORT); [ -n "$DB_PORT" ] || DB_PORT=3306
export MYSQL_PWD="$(env_get DB_PASSWORD)"

TOKEN_VIEW=$(mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -N -B "$DB_NAME" \
  -e "SELECT view_token FROM form_templates WHERE view_token IS NOT NULL ORDER BY id LIMIT 1;")
[ -n "$TOKEN_VIEW" ] || { echo "ABORTADO: nenhum view_token no banco."; exit 1; }
echo "view_token: $TOKEN_VIEW"

saude() { docker inspect --format='{{.State.Health.Status}}' fabiano-backend 2>/dev/null || echo ausente; }
subir() {
  docker compose up -d --no-deps --force-recreate --pull never backend >/dev/null 2>&1
  local i; for i in $(seq 1 150); do [ "$(saude)" = healthy ] && return 0; sleep 1; done
  return 1
}
restaurar() {
  echo
  echo ">>> restaurando"
  if [ -f "$BKP" ]; then cat "$BKP" > .env; rm -f "$BKP"; fi
  subir && echo "    backend: healthy" || echo "    backend: $(saude)  <<< OLHAR ISTO"
}
trap restaurar EXIT

sonda() {
  curl -s --max-time 30 --resolve "${HOST_API}:443:127.0.0.1" \
    -o /tmp/resposta-oiv.json -w '%{http_code}' \
    "https://${HOST_API}/form-templates/view/${TOKEN_VIEW}/submissions"
}

cp .env "$BKP"

for OIV in true false; do
  echo
  echo "============================================================"
  echo " open-in-view=$OIV"
  echo "============================================================"
  grep -v '^SPRING_JPA_OPEN_IN_VIEW=' "$BKP" > .env
  echo "SPRING_JPA_OPEN_IN_VIEW=${OIV}" >> .env
  subir || { echo "  backend nao subiu"; continue; }

  MARCA=$(date +%s)
  CODIGO=$(sonda)
  echo "  GET /form-templates/view/.../submissions -> HTTP ${CODIGO}"
  echo "  corpo da resposta:"
  head -c 400 /tmp/resposta-oiv.json | sed 's/^/    /'; echo

  echo
  echo "  o que o log diz (ultimos 40s):"
  docker logs --since 40s fabiano-backend 2>&1 \
    | grep -iE 'Lazy|could not initialize|no session|GlobalExceptionHandler|Erro nao previsto|Regra de negocio' \
    | tail -8 | cut -c1-400 | sed 's/^/    /'

  echo
  echo "  excecao raiz, se houver:"
  docker logs --since 40s fabiano-backend 2>&1 \
    | grep -oE '(org\.hibernate|jakarta\.persistence|java\.lang)\.[A-Za-z.]*Exception[^\\"]*' \
    | sort -u | head -5 | sed 's/^/    /'
done

echo
echo "============================================================"
echo " COMO LER"
echo "============================================================"
echo " Se com true der 200 e com false der 400 nomeando"
echo " LazyInitializationException, a rota precisa de fetch join antes"
echo " da virada — e o FABIANO-37 NAO pode ser fechado como esta."
