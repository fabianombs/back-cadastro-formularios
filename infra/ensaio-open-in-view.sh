#!/usr/bin/env bash
# =============================================================================
# ensaio-open-in-view.sh  (v2)                               (FABIANO-37)
#
# Mede o efeito de 'spring.jpa.open-in-view' com dados de producao restaurados.
#
#   FASE A  open-in-view=true   (como producao esta hoje)
#   FASE B  open-in-view=false  (o que o card propoe)
#
# POR QUE A v1 NAO VALEU. Ela usava rotas autenticadas do usuario de servico
# (/form-templates/my-templates, /clients/{id}/templates). O 'smoke_servico'
# nao tem Client associado — metade das requisicoes voltou 400 e o tempo medido
# ficou dominado pelo caminho de ERRO, que e curto e nao renderiza grafo nenhum.
# Justamente onde o open-in-view NAO pesa.
#
# Esta versao usa as rotas PUBLICAS de leitura, com slug e view_token lidos do
# banco. Sao as que devolvem template + campos + opcoes + aparencia, ou seja o
# grafo aninhado de verdade — e nao precisam de token.
#
# E se recusa a concluir qualquer coisa se as rotas nao responderem 2xx.
#
# SO RODA EM HOMOLOGACAO. Recria o backend duas vezes.
# =============================================================================
set -uo pipefail

DIR_DEPLOY="${DIR_DEPLOY:-/home/ec2-user/fabiano/deploy}"
HOST_API="api-hml.nexventa.com.br"
BKP=".env.ensaio-oiv"
REPETICOES="${REPETICOES:-10}"

cd "$DIR_DEPLOY" || { echo "ERRO: $DIR_DEPLOY nao existe"; exit 1; }
env_get() { grep -E "^${1}=" .env 2>/dev/null | head -1 | cut -d= -f2-; }

echo "============================================================"
echo " ENSAIO open-in-view (v2) — FABIANO-37"
echo " maquina: $(hostname)   $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M:%S')"
echo "============================================================"

if ! env_get DB_HOST | grep -q 'homolog'; then
  echo "ABORTADO: o .env desta maquina nao aponta para o banco de homologacao."
  exit 1
fi

DB_HOST=$(env_get DB_HOST); DB_NAME=$(env_get DB_NAME); DB_USER=$(env_get DB_USER)
DB_PORT=$(env_get DB_PORT); [ -n "$DB_PORT" ] || DB_PORT=3306
export MYSQL_PWD="$(env_get DB_PASSWORD)"
TOKEN_METRICAS=$(env_get METRICS_SCRAPE_TOKEN)
[ -n "$TOKEN_METRICAS" ] || { echo "ABORTADO: METRICS_SCRAPE_TOKEN ausente no .env"; exit 1; }

# O stderr NAO vai para /dev/null: uma consulta que erra por coluna inexistente
# devolveria vazio em silencio, e a lista de rotas encolheria sem ninguem notar.
# Foi o que aconteceu na primeira execucao da v2 — 'WHERE deleted = 0' em
# form_templates, coluna que a migration V24 apagou.
sql() { mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -N -B "$DB_NAME" -e "$1"; }
api() { curl -s --max-time 30 --resolve "${HOST_API}:443:127.0.0.1" "$@"; }
saude() { docker inspect --format='{{.State.Health.Status}}' fabiano-backend 2>/dev/null || echo ausente; }
subir() {
  docker compose up -d --no-deps --force-recreate --pull never backend >/dev/null 2>&1
  local i; for i in $(seq 1 150); do [ "$(saude)" = healthy ] && return 0; sleep 1; done
  return 1
}
restaurar() {
  echo
  echo ">>> restaurando o .env e o backend"
  if [ -f "$BKP" ]; then cat "$BKP" > .env; rm -f "$BKP"; fi
  subir && echo "    backend: healthy  (open-in-view de volta ao valor do properties)" \
        || echo "    backend: $(saude)  <<< OLHAR ISTO"
}
trap restaurar EXIT

# =============================================================================
# Monta a lista de rotas a partir do que EXISTE no banco.
# =============================================================================
echo
echo "=== montando a lista de rotas a partir do banco ==="
ROTAS=()
while read -r s; do [ -n "$s" ] && ROTAS+=("/form-templates/slug/${s}"); done < <(
  sql "SELECT slug FROM form_templates WHERE slug IS NOT NULL ORDER BY id LIMIT 3;")

while read -r v; do
  [ -z "$v" ] && continue
  ROTAS+=("/form-templates/view/${v}")
  ROTAS+=("/form-templates/view/${v}/submissions")
  ROTAS+=("/form-templates/view/${v}/attendance")
done < <(sql "SELECT view_token FROM form_templates WHERE view_token IS NOT NULL ORDER BY id LIMIT 2;")

while read -r q; do [ -n "$q" ] && { ROTAS+=("/quizzes/slug/${q}"); ROTAS+=("/quizzes/slug/${q}/ranking"); }; done < <(
  sql "SELECT slug FROM quiz_configs WHERE slug IS NOT NULL LIMIT 1;")

while read -r sv; do [ -n "$sv" ] && ROTAS+=("/surveys/slug/${sv}"); done < <(
  sql "SELECT slug FROM survey_configs WHERE slug IS NOT NULL LIMIT 1;")

# GET /clients/*/templates e permitAll no SecurityConfig. Escolhe o cliente com
# MAIS templates: e o que produz a resposta mais pesada, que e o ponto.
CLIENTE=$(sql "SELECT c.id FROM clients c JOIN form_templates t ON t.client_id = c.id
               WHERE c.deleted = 0
               GROUP BY c.id ORDER BY COUNT(*) DESC LIMIT 1;")
[ -n "$CLIENTE" ] && ROTAS+=("/clients/${CLIENTE}/templates")

if [ "${#ROTAS[@]}" -lt 3 ]; then
  echo "ABORTADO: so ${#ROTAS[@]} rota(s) montada(s). O banco desta maquina nao tem"
  echo "          dado suficiente para o ensaio significar alguma coisa."
  exit 1
fi
printf '  %s\n' "${ROTAS[@]}"
echo "  ${#ROTAS[@]} rotas x ${REPETICOES} repeticoes"

# =============================================================================
metricas() {
  docker exec fabiano-backend curl -s -H "X-Metrics-Token: ${TOKEN_METRICAS}" \
    http://localhost:8080/actuator/prometheus 2>/dev/null
}
valor() { metricas | awk -v m="$1" '$0 ~ "^"m"[{ ]" { print $NF; exit }'; }

USO_A=""; USO_B=""; TOTAL_A=""; TOTAL_B=""; USOS_A=""; USOS_B=""

fase() {
  local nome="$1" oiv="$2" i rota codigo total=0 falhas=0

  echo
  echo "============================================================"
  echo " $nome  (open-in-view=$oiv)"
  echo "============================================================"
  grep -v '^SPRING_JPA_OPEN_IN_VIEW=' "$BKP" > .env
  echo "SPRING_JPA_OPEN_IN_VIEW=${oiv}" >> .env
  subir || { echo "  ERRO: backend nao ficou saudavel"; docker logs --tail 30 fabiano-backend; return 1; }

  # Aquecimento: a primeira chamada de cada rota paga JIT, cache de metadata do
  # Hibernate e abertura de conexao. Medir isso junto sujaria a comparacao.
  for rota in "${ROTAS[@]}"; do api -o /dev/null "https://${HOST_API}${rota}" >/dev/null; done
  docker compose restart backend >/dev/null 2>&1
  local j; for j in $(seq 1 150); do [ "$(saude)" = healthy ] && break; sleep 1; done

  for i in $(seq 1 "$REPETICOES"); do
    for rota in "${ROTAS[@]}"; do
      codigo=$(api -o /dev/null -w '%{http_code}' "https://${HOST_API}${rota}")
      total=$((total+1))
      case "$codigo" in 2*) ;; *) falhas=$((falhas+1)); [ "$i" = "1" ] && echo "    $rota -> HTTP $codigo" ;; esac
    done
  done

  local pct=$(( falhas * 100 / total ))
  echo "  requisicoes: ${total}   nao-2xx: ${falhas} (${pct}%)"
  if [ "$pct" -gt 5 ]; then
    echo "  >>> MEDICAO INVALIDA: mais de 5% das requisicoes falhou."
    echo "      O tempo abaixo seria dominado pelo caminho de erro, que e curto e"
    echo "      nao renderiza grafo — exatamente onde o open-in-view nao pesa."
    return 1
  fi

  local us_s us_c media
  us_s=$(valor hikaricp_connections_usage_seconds_sum)
  us_c=$(valor hikaricp_connections_usage_seconds_count)
  media=$(awk -v s="${us_s:-0}" -v c="${us_c:-0}" 'BEGIN{printf "%.2f", (c>0? s/c*1000 : 0)}')

  echo
  echo "  HikariCP:"
  echo "    usos de conexao ....... ${us_c:-?}"
  echo "    tempo medio por uso ... ${media} ms"
  # A media sozinha engana: com open-in-view=false a MESMA requisicao pega a
  # conexao mais vezes (uma por transacao, em vez de uma pela requisicao
  # inteira). Menos tempo em cada uso, mais usos. Quem decide e o total.
  echo "    TEMPO TOTAL DE POSSE .. $(awk -v s="${us_s:-0}" 'BEGIN{printf "%.0f", s*1000}') ms   <<< o numero deste card"
  echo "    ativas / ociosas / fila: $(valor hikaricp_connections_active) / $(valor hikaricp_connections_idle) / $(valor hikaricp_connections_pending)"

  local lazies
  lazies=$(docker logs fabiano-backend 2>&1 | grep -c 'LazyInitializationException')
  echo "    LazyInitializationException: ${lazies}"
  if [ "$oiv" = "false" ] && [ "$lazies" -gt 0 ]; then
    echo "    >>> com open-in-view=false apareceu LazyInitializationException:"
    docker logs fabiano-backend 2>&1 | grep 'LazyInitializationException' | tail -3 | sed 's/^/      /'
  fi

  if [ "$oiv" = "true" ]; then USO_A="$media"; TOTAL_A="${us_s:-0}"; USOS_A="${us_c:-0}"
  else USO_B="$media"; TOTAL_B="${us_s:-0}"; USOS_B="${us_c:-0}"; fi
}

cp .env "$BKP"
fase "FASE A — como producao esta hoje" true
fase "FASE B — o que o card propoe"     false

echo
echo "============================================================"
echo " VEREDITO"
echo "============================================================"
if [ -n "$USO_A" ] && [ -n "$USO_B" ]; then
  awk -v a="$USO_A" -v b="$USO_B" -v ta="$TOTAL_A" -v tb="$TOTAL_B" -v na="$USOS_A" -v nb="$USOS_B" 'BEGIN{
    printf " usos de conexao .........  %d (true)  ->  %d (false)\n", na, nb;
    printf " tempo medio por uso .....  %.2f ms  ->  %.2f ms", a, b;
    if (a > 0) printf "   (%+.1f%%)", (b-a)/a*100; printf "\n";
    printf " TEMPO TOTAL DE POSSE ....  %.0f ms  ->  %.0f ms", ta*1000, tb*1000;
    if (ta > 0) printf "   (%+.1f%%)", (tb-ta)/ta*100; printf "\n";
  }'
else
  echo " Pelo menos uma das fases foi invalidada. Nada a comparar."
fi
