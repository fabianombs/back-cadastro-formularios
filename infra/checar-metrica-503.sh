#!/usr/bin/env bash
# =============================================================================
# checar-metrica-503.sh  (v2)                  (FABIANO-56 e FABIANO-61)
#
# v1 mediu com a maquina JA restaurada para a configuracao antiga — o ensaio
# anterior desfaz o proprio ajuste ao sair. Resultado: 504 aos 60s e contador
# nenhum. Isso nao foi um erro de medicao inutil: e a prova do defeito do
# FABIANO-61 no seu pior aspecto — no 504 a requisicao nem chega ao
# GlobalExceptionHandler, entao a queda do banco passa INVISIVEL pela metrica.
#
# Esta versao aplica o socketTimeout, mede, e so entao restaura.
#
# Bloqueia a porta 3306 duas vezes, por ~1 minuto no total. So em homologacao.
# =============================================================================
set -uo pipefail

DIR_DEPLOY="${DIR_DEPLOY:-/home/ec2-user/fabiano/deploy}"
HOST_API="api-hml.nexventa.com.br"
BKP=".env.checar-metrica"

cd "$DIR_DEPLOY" || { echo "ERRO: $DIR_DEPLOY nao existe"; exit 1; }
env_get() { grep -E "^${1}=" .env 2>/dev/null | head -1 | cut -d= -f2-; }

echo "maquina: $(hostname)   $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M:%S')"
if ! env_get DB_HOST | grep -q 'homolog'; then
  echo "ABORTADO: o .env desta maquina nao aponta para o banco de homologacao."
  exit 1
fi
DB_HOST=$(env_get DB_HOST); DB_NAME=$(env_get DB_NAME)
DB_PORT=$(env_get DB_PORT); [ -n "$DB_PORT" ] || DB_PORT=3306

saude() { docker inspect --format='{{.State.Health.Status}}' fabiano-backend 2>/dev/null || echo ausente; }
subir_backend() {
  docker compose up -d --no-deps --force-recreate --pull never backend >/dev/null 2>&1
  local i; for i in $(seq 1 150); do [ "$(saude)" = healthy ] && return 0; sleep 1; done
  return 1
}
desbloquear() {
  while sudo iptables -D DOCKER-USER -p tcp --dport "$DB_PORT" -j REJECT 2>/dev/null; do :; done
}
restaurar() {
  echo
  echo ">>> restaurando"
  desbloquear
  echo "    regras restantes para a porta $DB_PORT: $(sudo iptables -S DOCKER-USER 2>/dev/null | grep -c -- "--dport $DB_PORT")"
  if [ -f "$BKP" ]; then cat "$BKP" > .env; rm -f "$BKP"; echo "    .env devolvido ao original"; fi
  subir_backend && echo "    backend: healthy" || echo "    backend: $(saude)  <<< OLHAR ISTO"
  echo "    sonda final: HTTP $(curl -s -o /dev/null -w '%{http_code}' --max-time 20 --resolve ${HOST_API}:443:127.0.0.1 -X POST https://${HOST_API}/auth/login -H 'Content-Type: application/json' -d '{"username":"x","password":"y"}')  (4xx = banco de volta)"
}
trap restaurar EXIT

# /actuator/prometheus exige o X-Metrics-Token (SecurityConfig.coletorAutorizado).
# A v2 deste script chamava sem o header e recebia 401 — e o grep vazio virava
# "a metrica nao existe". Era um teste incapaz de distinguir ausente de
# inacessivel, exatamente o erro que este projeto ja pagou duas vezes hoje.
TOKEN=$(env_get METRICS_SCRAPE_TOKEN)

metricas() {
  docker exec fabiano-backend curl -s -H "X-Metrics-Token: ${TOKEN}" \
    http://localhost:8080/actuator/prometheus 2>/dev/null | grep '^erro_tratado_total'
}

# Prova, ANTES de concluir qualquer coisa, que o endpoint esta acessivel. Sem
# isto, "nenhuma serie 503" e uma frase sem valor.
conferir_acesso() {
  local codigo
  codigo=$(docker exec fabiano-backend curl -s -o /dev/null -w '%{http_code}' \
            -H "X-Metrics-Token: ${TOKEN}" http://localhost:8080/actuator/prometheus 2>/dev/null)
  echo "  /actuator/prometheus responde HTTP ${codigo} (esperado 200)"
  [ "$codigo" = "200" ]
}
sonda() {
  curl -s -o /dev/null -w '%{http_code} em %{time_total}s' --max-time 75 \
    --resolve "${HOST_API}:443:127.0.0.1" \
    -X POST "https://${HOST_API}/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"ensaio_metrica","password":"nao-existe"}'
}

# -----------------------------------------------------------------------------
echo
echo "=== aplicando o socketTimeout e recriando o backend ==="
cp .env "$BKP"
{
  echo "SPRING_DATASOURCE_URL=jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslMode=REQUIRED&serverTimezone=UTC&connectTimeout=5000&socketTimeout=15000"
  echo "SPRING_DATASOURCE_HIKARI_KEEPALIVE_TIME=30000"
  echo "SPRING_DATASOURCE_HIKARI_VALIDATION_TIMEOUT=3000"
} >> .env
subir_backend || { echo "ERRO: backend nao ficou saudavel"; docker logs --tail 30 fabiano-backend; exit 1; }
echo "  backend healthy com socketTimeout=15000"

echo
echo "=== acesso a metrica ==="
if [ -z "$TOKEN" ]; then
  echo "  ERRO: METRICS_SCRAPE_TOKEN ausente no .env — nao da para ler a metrica."
  exit 1
fi
conferir_acesso || { echo "  ERRO: sem acesso ao endpoint. Abortando antes de concluir bobagem."; exit 1; }

echo
echo "=== ANTES do bloqueio ==="
echo "  requisicao com o banco no ar: $(sonda)"
echo "  contadores:"
metricas | sed 's/^/    /' || true
metricas >/dev/null || echo "    (nenhum erro_tratado_total ainda)"

echo
echo "=== COM O BANCO FORA ==="
sudo iptables -I DOCKER-USER 1 -p tcp --dport "$DB_PORT" -j REJECT
echo "  requisicao com o banco bloqueado: $(sonda)"
desbloquear
sleep 3

echo
echo "=== DEPOIS ==="
echo "  contadores:"
metricas | sed 's/^/    /' || echo "    (nenhum)"

echo
echo "=== O painel de taxa de erro enxerga a indisponibilidade? ==="
# O painel soma 5xx. O que importa e existir serie com status=503: se so
# houvesse 400, a queda do banco seguiria contando como erro de usuario — que e
# exatamente o defeito descrito no FABIANO-56.
if metricas | grep -q 'status="503"'; then
  echo "  OK — existe serie erro_tratado_total com status=\"503\""
else
  echo "  FALHOU — nenhuma serie com status=\"503\""
fi
