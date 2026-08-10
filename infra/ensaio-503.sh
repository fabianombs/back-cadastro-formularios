#!/usr/bin/env bash
# =============================================================================
# ensaio-503.sh                                (FABIANO-56 e FABIANO-61)
#
# Derruba o acesso ao banco e observa o que o usuario recebe.
#
#   FASE A  como esta hoje      -> esperado: a PRIMEIRA requisicao toma 504 do
#                                  nginx, e so as seguintes viram 503
#   FASE B  com socketTimeout   -> esperado: ja a primeira devolve 503 com
#                                  Retry-After, e nenhum 504 na sequencia
#
# A sonda e POST /auth/login com credencial inexistente: e rota publica, toca o
# banco em toda chamada, e nao precisa de token. Com o banco no ar responde 4xx;
# com o banco fora, quem responde e o GlobalExceptionHandler.
#
# SO RODA EM HOMOLOGACAO. Bloqueia a porta 3306 da maquina por alguns minutos.
# O trap desfaz a regra de iptables e devolve o .env em qualquer saida.
# =============================================================================
set -uo pipefail

DIR_DEPLOY="${DIR_DEPLOY:-/home/ec2-user/fabiano/deploy}"
HOST_API="api-hml.nexventa.com.br"
HDR="$HOME/.ensaio-503-headers"
BKP=".env.ensaio-503"

cd "$DIR_DEPLOY" || { echo "ERRO: $DIR_DEPLOY nao existe"; exit 1; }
env_get() { grep -E "^${1}=" .env 2>/dev/null | head -1 | cut -d= -f2-; }

echo "============================================================"
echo " ENSAIO — banco fora do ar: 503 ou 504?   (FABIANO-56/61)"
echo " maquina: $(hostname)   $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M:%S')"
echo "============================================================"

if ! env_get DB_HOST | grep -q 'homolog'; then
  echo "ABORTADO: o .env desta maquina nao aponta para o banco de homologacao."
  exit 1
fi

DB_HOST=$(env_get DB_HOST); DB_PORT=$(env_get DB_PORT); DB_NAME=$(env_get DB_NAME)
[ -n "$DB_PORT" ] || DB_PORT=3306

saude() { docker inspect --format='{{.State.Health.Status}}' fabiano-backend 2>/dev/null || echo ausente; }

esperar_saudavel() {
  local i
  for i in $(seq 1 150); do [ "$(saude)" = healthy ] && return 0; sleep 1; done
  return 1
}

subir_backend() {
  docker compose up -d --no-deps --force-recreate --pull never backend >/dev/null 2>&1
  esperar_saudavel
}

bloquear() {
  # DOCKER-USER e a cadeia que a Docker deixa livre para o operador: ela e
  # consultada antes das regras que a propria Docker gera, e sobrevive a
  # 'docker compose up'. Bloquear na OUTPUT nao funcionaria — o trafego do
  # container e FORWARD, nao OUTPUT.
  sudo iptables -I DOCKER-USER 1 -p tcp --dport "$DB_PORT" -j REJECT
}

desbloquear() {
  # Em laco: se o ensaio for interrompido e reexecutado, pode haver mais de uma
  # regra empilhada. Sair no primeiro erro deixaria a homolog sem banco.
  while sudo iptables -D DOCKER-USER -p tcp --dport "$DB_PORT" -j REJECT 2>/dev/null; do :; done
}

restaurar() {
  echo
  echo "============================================================"
  echo " RESTAURANDO"
  echo "============================================================"
  desbloquear
  echo " regras restantes na DOCKER-USER para a porta $DB_PORT: $(sudo iptables -S DOCKER-USER 2>/dev/null | grep -c -- "--dport $DB_PORT")"
  if [ -f "$BKP" ]; then
    cat "$BKP" > .env          # '>' e nao 'mv': preserva o inode do .env
    rm -f "$BKP"
    echo " .env devolvido ao original"
  fi
  rm -f "$HDR"
  subir_backend && echo " backend: healthy" || echo " backend: $(saude)  <<< OLHAR ISTO"
  docker exec fabiano-nginx nginx -s reload >/dev/null 2>&1 && echo " nginx recarregado"
  echo " sonda final: $(curl -s -o /dev/null -w '%{http_code}' --max-time 20 --resolve ${HOST_API}:443:127.0.0.1 -X POST https://${HOST_API}/auth/login -H 'Content-Type: application/json' -d '{"username":"x","password":"y"}')  (esperado 4xx = banco respondendo)"
}
trap restaurar EXIT

sondar() {
  local rot="$1" out ra
  rm -f "$HDR"
  out=$(curl -s -o /dev/null -D "$HDR" --max-time 75 \
        --resolve "${HOST_API}:443:127.0.0.1" \
        -X POST "https://${HOST_API}/auth/login" \
        -H 'Content-Type: application/json' \
        -d '{"username":"ensaio_503","password":"nao-existe"}' \
        -w '%{http_code} %{time_total}')
  ra=$(grep -i '^retry-after:' "$HDR" 2>/dev/null | tr -d '\r' | cut -d' ' -f2-)
  # shellcheck disable=SC2086
  printf '    %-14s HTTP %s  em %ss   Retry-After: %s\n' "$rot" $out "${ra:-<ausente>}"
}

rodar_fase() {
  local nome="$1"
  echo
  echo "--- $nome: banco no ar (referencia) ---"
  sondar "referencia"
  echo "--- $nome: bloqueando a porta $DB_PORT ---"
  bloquear
  sondar "1a requisicao"
  sondar "2a"
  sondar "3a"
  echo "--- $nome: liberando ---"
  desbloquear
  sleep 5
  sondar "depois"
}

# =============================================================================
rodar_fase "FASE A (como esta hoje)"

# =============================================================================
echo
echo "============================================================"
echo " Aplicando o timeout de socket e recriando o backend"
echo "============================================================"
# O Connector/J tem socketTimeout=0 por padrao — leitura sem prazo. O
# hikari.connection-timeout=5000 nao cobre isso: ele limita a espera POR uma
# conexao do pool, nao a leitura numa conexao ja entregue. Com o banco fora, a
# primeira requisicao pega uma conexao aparentemente boa, escreve nela e espera
# para sempre — ate o nginx desistir aos 60s e devolver 504.
#
# Testado aqui por variavel de ambiente para nao precisar de build: o Spring
# aceita SPRING_DATASOURCE_URL por cima do application-*.properties.
cp .env "$BKP"
URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?sslMode=REQUIRED&serverTimezone=UTC&connectTimeout=5000&socketTimeout=15000"
{
  echo "SPRING_DATASOURCE_URL=${URL}"
  echo "SPRING_DATASOURCE_HIKARI_KEEPALIVE_TIME=30000"
  echo "SPRING_DATASOURCE_HIKARI_VALIDATION_TIMEOUT=3000"
} >> .env
echo "  SPRING_DATASOURCE_URL com connectTimeout=5000 e socketTimeout=15000"

if subir_backend; then
  echo "  backend healthy com a nova configuracao"
  rodar_fase "FASE B (com socketTimeout)"
else
  echo "  ERRO: o backend nao ficou saudavel com a nova URL. Fase B cancelada."
  docker logs --tail 30 fabiano-backend 2>&1 | sed 's/^/    /'
fi

echo
echo "============================================================"
echo " COMO LER"
echo "============================================================"
echo " FABIANO-56: qualquer 503 (em vez de 400) com Retry-After ja atende."
echo " FABIANO-61: o que fecha o card e a 1a requisicao da FASE B NAO ser 504."
echo "             HTTP 000 = o curl desistiu antes; conta como pior que 504."
