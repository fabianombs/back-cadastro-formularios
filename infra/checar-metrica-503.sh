#!/usr/bin/env bash
# =============================================================================
# checar-metrica-503.sh                        (FABIANO-56 e FABIANO-61)
#
# O ultimo criterio dos dois cards: o contador de erro chega a subir com status
# 503, ou a indisponibilidade passa invisivel pelo monitoramento?
#
# Le a metrica direto do /actuator/prometheus da aplicacao, e nao do Prometheus:
# o ensaio anterior recriou o container e zerou o contador, e alem disso o
# Prometheus da homolog nao publica porta no host — ele vive so dentro da rede
# do compose. Este script mostra isso tambem, no fim.
#
# Bloqueia a porta 3306 por ~40 segundos. So roda em homologacao.
# =============================================================================
set -uo pipefail

DIR_DEPLOY="${DIR_DEPLOY:-/home/ec2-user/fabiano/deploy}"
HOST_API="api-hml.nexventa.com.br"

cd "$DIR_DEPLOY" || { echo "ERRO: $DIR_DEPLOY nao existe"; exit 1; }
env_get() { grep -E "^${1}=" .env 2>/dev/null | head -1 | cut -d= -f2-; }

echo "maquina: $(hostname)   $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M:%S')"
if ! env_get DB_HOST | grep -q 'homolog'; then
  echo "ABORTADO: o .env desta maquina nao aponta para o banco de homologacao."
  exit 1
fi
DB_PORT=$(env_get DB_PORT); [ -n "$DB_PORT" ] || DB_PORT=3306

desbloquear() {
  while sudo iptables -D DOCKER-USER -p tcp --dport "$DB_PORT" -j REJECT 2>/dev/null; do :; done
}
restaurar() {
  echo
  desbloquear
  echo ">>> regras restantes para a porta $DB_PORT: $(sudo iptables -S DOCKER-USER 2>/dev/null | grep -c -- "--dport $DB_PORT")"
  echo ">>> sonda final: HTTP $(curl -s -o /dev/null -w '%{http_code}' --max-time 20 --resolve ${HOST_API}:443:127.0.0.1 -X POST https://${HOST_API}/auth/login -H 'Content-Type: application/json' -d '{"username":"x","password":"y"}')  (4xx = banco de volta)"
}
trap restaurar EXIT

metricas() {
  docker exec fabiano-backend curl -s http://localhost:8080/actuator/prometheus \
    | grep '^erro_tratado_total' | sed 's/^/    /'
}

sonda() {
  curl -s -o /dev/null -w '%{http_code} em %{time_total}s\n' --max-time 75 \
    --resolve "${HOST_API}:443:127.0.0.1" \
    -X POST "https://${HOST_API}/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"username":"ensaio_metrica","password":"nao-existe"}'
}

echo
echo "=== ANTES ==="
echo "  requisicao com o banco no ar: $(sonda)"
echo "  contadores:"
metricas || echo "    (nenhum erro_tratado_total ainda)"

echo
echo "=== COM O BANCO FORA ==="
sudo iptables -I DOCKER-USER 1 -p tcp --dport "$DB_PORT" -j REJECT
echo "  requisicao com o banco bloqueado: $(sonda)"
desbloquear
sleep 3

echo
echo "=== DEPOIS ==="
echo "  contadores:"
metricas || echo "    (nenhum)"

echo
echo "=== O painel enxerga? ==="
# O painel de taxa de erro soma 5xx. O que importa e existir serie com
# status=503; se so houvesse 400, a queda do banco continuaria contando como
# erro de usuario — que e o defeito que o FABIANO-56 descreve.
if metricas | grep -q 'status="503"'; then
  echo "  OK — existe serie com status=\"503\""
else
  echo "  FALHOU — nenhuma serie com status=\"503\""
fi

echo
echo "=== Por que localhost:9090 nao respondeu ==="
docker ps --format '{{.Names}}  |  {{.Ports}}' | sed 's/^/    /'
