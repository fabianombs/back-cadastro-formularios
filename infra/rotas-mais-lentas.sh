#!/usr/bin/env bash
# =============================================================================
# rotas-mais-lentas.sh                                        (FABIANO-80)
#
# SOMENTE LEITURA.
#
# Responde: qual e a rota mais lenta que o sistema ja atendeu, de verdade?
#
# O criterio do FABIANO-80 exige escolher o socketTimeout a partir da latencia
# REAL da pior rota, e nao por estimativa. Um socketTimeout abaixo do tempo de
# uma consulta legitima transforma essa consulta em erro 503.
#
# POR QUE NAO SERVE LER O /actuator/prometheus DIRETO
#
# O 'http_server_requests_seconds_max' do Micrometer e uma janela DESLIZANTE de
# 2 minutos, nao o maximo historico. Lido direto, ele mostra so o que aconteceu
# nos ultimos 2 minutos — que costuma ser a propria sonda de quem esta olhando.
#
# Quem guarda historico e o Prometheus. Ele nao publica porta no host, mas esta
# na rede 'fabiano-internal' do compose, e o container do backend tem curl.
# =============================================================================
set -uo pipefail

DIR_DEPLOY="${DIR_DEPLOY:-/home/ec2-user/fabiano/deploy}"
JANELA="${JANELA:-7d}"

cd "$DIR_DEPLOY" || { echo "ERRO: $DIR_DEPLOY nao existe"; exit 1; }
env_get() { grep -E "^${1}=" .env 2>/dev/null | head -1 | cut -d= -f2-; }

echo "maquina: $(hostname)   $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M')"
echo "janela: ultimos ${JANELA}"

# Consulta o Prometheus de dentro da rede do compose.
prom() {
  docker exec fabiano-backend curl -s -G \
    --data-urlencode "query=$1" \
    http://fabiano-prometheus:9090/api/v1/query 2>/dev/null
}

# Prova o acesso antes de concluir qualquer coisa: sem isso, resposta vazia
# viraria "nao ha rota lenta", que e diferente de "nao consegui perguntar".
TESTE=$(prom 'up')
if ! echo "$TESTE" | grep -q '"status":"success"'; then
  echo "ABORTADO: nao consegui falar com o Prometheus de dentro da rede do compose."
  echo "Resposta: $(echo "$TESTE" | head -c 200)"
  exit 1
fi
echo "Prometheus respondeu. Seguindo."

mostrar() {
  # 'nan' aparece em rota com ZERO chamadas na janela (0/0) e 'inf' quando ha
  # soma sem contagem. Nao sao tempos — sao ausencia de amostra, e imprimi-los
  # como se fossem numero confunde quem le.
  python3 -c '
import sys, json, math
d = json.load(sys.stdin)
r = d.get("data", {}).get("result", [])
if not r:
    print("    (nenhuma serie no periodo)")
    raise SystemExit
linhas, sem_amostra = [], 0
for s in r:
    v = float(s["value"][1])
    m = s["metric"]
    if math.isnan(v) or math.isinf(v):
        sem_amostra += 1
        continue
    linhas.append((v, "%-7s %-40s %s" % (m.get("method","?"), m.get("uri","?"), m.get("status",""))))
if not linhas:
    print("    (nenhuma rota com amostra na janela)")
for v, rot in sorted(linhas, reverse=True)[:15]:
    print("    %8.3f s   %s" % (v, rot))
if sem_amostra:
    print("    (%d rota(s) sem nenhuma chamada na janela, omitidas)" % sem_amostra)
'
}

# A contagem NAO e tempo. Imprimir com \'s\' no fim, como o mostrar() faz,
# faria 3726 requisicoes parecerem 3726 segundos.
contar() {
  python3 -c '
import sys, json
d = json.load(sys.stdin)
r = d.get("data", {}).get("result", [])
print("    %s requisicoes" % (int(float(r[0]["value"][1])) if r else 0))
'
}

echo
echo "=== PIOR TEMPO ja observado por rota (max_over_time do maximo) ==="
prom "max_over_time(http_server_requests_seconds_max{uri!~\"/actuator.*\"}[${JANELA}])" | mostrar

echo
echo "=== TEMPO MEDIO por rota (soma / contagem, mesma janela) ==="
prom "sum by (method,uri) (increase(http_server_requests_seconds_sum{uri!~\"/actuator.*\"}[${JANELA}])) / sum by (method,uri) (increase(http_server_requests_seconds_count{uri!~\"/actuator.*\"}[${JANELA}]))" | mostrar

echo
echo "=== quantas requisicoes houve na janela (para saber se a amostra vale) ==="
prom "sum(increase(http_server_requests_seconds_count{uri!~\"/actuator.*\"}[${JANELA}]))" | contar

echo
echo "============================================================"
echo " COMO LER"
echo "============================================================"
echo " O socketTimeout precisa ficar CONFORTAVELMENTE acima do pior tempo."
echo " E, como o ensaio mediu 'resposta = 2 x socketTimeout', ele tambem"
echo " precisa ficar abaixo de 30s — senao a queda do banco volta a estourar"
echo " os 60s do nginx e vira 504 de novo."
echo
echo " Se a contagem de requisicoes for baixa, a amostra e fraca: o pior tempo"
echo " observado pode simplesmente nao ter acontecido ainda."
