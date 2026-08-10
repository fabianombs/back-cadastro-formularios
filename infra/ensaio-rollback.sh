#!/usr/bin/env bash
# =============================================================================
# ensaio-rollback.sh                                          (FABIANO-2)
#
# Prova, em HOMOLOGACAO, os tres comportamentos que o card exige e que nunca
# foram observados de verdade:
#
#   ATO 1  imagem que nao existe no registry  -> deploy aborta ANTES de encostar
#                                                no servico (exit 2)
#   ATO 2  imagem que nunca fica saudavel     -> health-gate reprova e o
#                                                rollback automatico devolve a
#                                                versao anterior (exit 1)
#   ATO 3  rollback.sh manual                 -> lista as versoes disponiveis e
#                                                volta para a tag escolhida
#
# NAO RODAR EM PRODUCAO. O ato 2 derruba o backend de proposito por ~3 minutos.
# A primeira coisa que o script faz e conferir se o .env aponta para o banco de
# homologacao; se nao apontar, ele sai sem tocar em nada.
#
# Uso (na maquina de homologacao):
#   ./ensaio-rollback.sh
# =============================================================================
set -uo pipefail

DIR_DEPLOY="${DIR_DEPLOY:-/home/ec2-user/fabiano/deploy}"
IMG_ENSAIO_TAG="ensaio-quebrado"
TAG_FANTASMA="ensaio-tag-que-nao-existe"
LOG="$HOME/ensaio-rollback.log"

echo "============================================================"
echo " ENSAIO DE DEPLOY QUEBRADO E ROLLBACK  —  FABIANO-2"
echo " maquina: $(hostname)"
echo " data:    $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M:%S') (BRT)"
echo "============================================================"

cd "$DIR_DEPLOY" || { echo "ERRO: $DIR_DEPLOY nao existe nesta maquina."; exit 1; }

env_get() { grep -E "^${1}=" .env 2>/dev/null | head -1 | cut -d= -f2-; }

# -----------------------------------------------------------------------------
# GUARDA — so homologacao.
# -----------------------------------------------------------------------------
# O ato 2 sobe uma imagem que nunca responde. Numa maquina de producao isso e
# uma indisponibilidade real. A checagem e pelo DB_HOST porque e o unico campo
# que difere de forma inequivoca entre as duas maquinas: o compose, o nome do
# container e o caminho sao identicos nos dois lados (mesma AMI).
if ! env_get DB_HOST | grep -q 'homolog'; then
  echo
  echo "ABORTADO: o .env desta maquina NAO aponta para o banco de homologacao."
  echo "          Este ensaio derruba o backend de proposito e nao pode rodar aqui."
  exit 1
fi
echo
echo "guarda ok — DB_HOST e de homologacao."

GHCR_OWNER=$(env_get GHCR_OWNER)
TAG_BOA=$(env_get BACKEND_TAG)
DB_PASSWORD=$(env_get DB_PASSWORD)
IMAGEM="ghcr.io/${GHCR_OWNER}/fabiano-back"

[ -n "$GHCR_OWNER" ] || { echo "ERRO: GHCR_OWNER ausente no .env"; exit 1; }
[ -n "$TAG_BOA" ]    || { echo "ERRO: BACKEND_TAG ausente no .env"; exit 1; }

if [ "$TAG_BOA" = "previous" ]; then
  echo
  echo "ABORTADO: BACKEND_TAG ja esta em 'previous' — sobra de um ensaio anterior."
  echo "          Volte para a tag real antes de repetir:"
  echo "            ./scripts/rollback.sh <sha-da-versao-boa>"
  exit 1
fi

ID_BOM=$(docker inspect --format='{{.Image}}' fabiano-backend 2>/dev/null || echo "")
echo "versao boa: ${IMAGEM}:${TAG_BOA}"
echo "image id:   ${ID_BOM:-<nenhum container em execucao>}"

# Imprime um log de deploy sem deixar vazar a senha do banco. O deploy-safe.sh
# ECOA o comando mysql completo (com -p<senha>) no ramo 'migration destrutiva'
# do analisar_banco. A senha vai por process substitution, nunca por argv.
mostrar_log() {
  if [ -n "$DB_PASSWORD" ] && grep -qF -f <(printf '%s\n' "$DB_PASSWORD") "$1" 2>/dev/null; then
    echo "    (AVISO: a saida continha a senha do banco — essas linhas foram suprimidas)"
    grep -vF -f <(printf '%s\n' "$DB_PASSWORD") "$1" | sed 's/^/    /'
  else
    sed 's/^/    /' "$1"
  fi
}

saude() { docker inspect --format='{{.State.Health.Status}}' fabiano-backend 2>/dev/null || echo ausente; }
id_atual() { docker inspect --format='{{.Image}}' fabiano-backend 2>/dev/null || echo ""; }

esperar_saudavel() {
  local i
  for i in $(seq 1 150); do
    [ "$(saude)" = "healthy" ] && return 0
    sleep 1
  done
  return 1
}

# -----------------------------------------------------------------------------
# Restauracao — roda em QUALQUER saida, inclusive Ctrl-C ou erro no meio.
# -----------------------------------------------------------------------------
restaurar() {
  echo
  echo "============================================================"
  echo " RESTAURANDO O ESTADO ORIGINAL"
  echo "============================================================"
  rm -f docker-compose.override.yml scripts/deploy-ensaio.sh
  sed -i "s|^BACKEND_TAG=.*|BACKEND_TAG=${TAG_BOA}|" .env

  # Se o ensaio terminou como devia, o servico ja esta na versao certa e
  # saudavel — recriar o container aqui so somaria mais uma janela de 502.
  if [ "$(id_atual)" = "$ID_BOM" ] && [ "$(saude)" = "healthy" ]; then
    echo " backend ja estava na versao boa e saudavel — nada a recriar"
  else
    BACKEND_TAG="$TAG_BOA" docker compose up -d --no-deps --force-recreate --pull never backend >/dev/null 2>&1
    esperar_saudavel && echo " backend: healthy" || echo " backend: $(saude)  <<< OLHAR ISTO"
  fi

  docker rmi "${IMAGEM}:${IMG_ENSAIO_TAG}" >/dev/null 2>&1
  docker rm -f ensaio-tmp >/dev/null 2>&1

  docker exec fabiano-nginx nginx -s reload >/dev/null 2>&1 && echo " nginx recarregado"
  echo " BACKEND_TAG no .env: $(env_get BACKEND_TAG)"
  echo " imagem em execucao:  $(id_atual)"
  [ "$(id_atual)" = "$ID_BOM" ] && echo " confere com a versao boa do inicio do ensaio." \
                                || echo " NAO confere com a versao do inicio — conferir a mao."
}
trap restaurar EXIT

RES1="?"; RES2="?"; RES3="?"

# =============================================================================
echo
echo "============================================================"
echo " ATO 1 — deploy de uma tag que nao existe no registry"
echo "============================================================"
# =============================================================================
rm -f "$LOG"
./scripts/deploy-safe.sh "$TAG_FANTASMA" > "$LOG" 2>&1
RC1=$?
mostrar_log "$LOG"
echo
echo "  codigo de saida: $RC1   (esperado: 2 = abortado, servico intacto)"
if [ "$RC1" = "2" ] && [ "$(id_atual)" = "$ID_BOM" ] && [ "$(saude)" = "healthy" ]; then
  RES1="OK — abortou no pull e o container nao foi tocado"
else
  RES1="FALHOU — rc=$RC1, saude=$(saude), imagem mudou? $([ "$(id_atual)" = "$ID_BOM" ] && echo nao || echo SIM)"
fi
echo "  $RES1"

# =============================================================================
echo
echo "============================================================"
echo " ATO 2 — deploy de uma imagem que nunca fica saudavel"
echo "============================================================"
# =============================================================================

# A imagem quebrada e a PROPRIA imagem boa com o entrypoint trocado: mesma base,
# mesmo HEALTHCHECK, mesma configuracao. O unico defeito e que a aplicacao nunca
# sobe — que e exatamente o que um deploy ruim faz. 'docker commit' em vez de
# 'docker build' para nao copiar camada nenhuma.
echo "  montando ${IMAGEM}:${IMG_ENSAIO_TAG} a partir da versao boa..."
docker rm -f ensaio-tmp >/dev/null 2>&1
docker create --name ensaio-tmp "${IMAGEM}:${TAG_BOA}" >/dev/null || { echo "  ERRO: nao consegui criar o container base"; exit 1; }
docker commit \
  --change 'ENTRYPOINT ["sh","-c","echo ENSAIO: aplicacao quebrada de proposito; sleep 3600"]' \
  --change 'CMD []' \
  ensaio-tmp "${IMAGEM}:${IMG_ENSAIO_TAG}" >/dev/null || { echo "  ERRO: docker commit falhou"; exit 1; }
docker rm ensaio-tmp >/dev/null

# Sem HEALTHCHECK na imagem o health-gate leria 'ausente' para sempre e o ensaio
# provaria a coisa errada — o gate tem que reprovar por saude, nao por ausencia.
HC=$(docker inspect --format='{{if .Config.Healthcheck}}{{.Config.Healthcheck.Test}}{{else}}NENHUM{{end}}' "${IMAGEM}:${IMG_ENSAIO_TAG}")
echo "  healthcheck herdado: $HC"
if [ "$HC" = "NENHUM" ]; then
  echo "  ERRO: a imagem de ensaio ficou sem healthcheck. Abortando."
  exit 1
fi

# A imagem quebrada so existe NESTA maquina. O compose declara
# 'pull_policy: always', entao sem este override ele iria busca-la no GHCR e o
# ensaio pararia no pull, sem nunca chegar ao health-gate.
cat > docker-compose.override.yml <<'EOF'
# TEMPORARIO — ensaio de rollback (FABIANO-2). Apagado pelo proprio ensaio.
services:
  backend:
    pull_policy: never
EOF
echo "  override temporario criado (pull_policy: never)"

echo "  subindo a imagem quebrada — o health-gate espera ate 120s antes de desistir"
echo
rm -f "$LOG"
./scripts/deploy-safe.sh "$IMG_ENSAIO_TAG" > "$LOG" 2>&1
RC2=$?

# Se o compose ignorar o override e insistir em ir ao registry, o script sai com
# 2 no pull e o ensaio nao testa nada. Nesse caso repete com uma copia do
# deploy-safe.sh com UMA unica linha neutralizada — e mostra o diff, para nao
# restar duvida sobre o que foi alterado.
if [ "$RC2" = "2" ] && grep -q "falhou ao puxar" "$LOG"; then
  echo "  o compose foi ao registry apesar do override. Repetindo com o pull neutralizado."
  sed 's|if ! $COMPOSE pull "$SERVICO"; then|if false; then|' scripts/deploy-safe.sh > scripts/deploy-ensaio.sh
  if ! grep -q 'if false; then' scripts/deploy-ensaio.sh || grep -q '$COMPOSE pull' scripts/deploy-ensaio.sh; then
    echo "  ERRO: o sed nao encontrou a linha do pull. Abortando (nada foi alterado no original)."
    exit 1
  fi
  chmod +x scripts/deploy-ensaio.sh
  echo "  diferenca em relacao ao script original:"
  diff scripts/deploy-safe.sh scripts/deploy-ensaio.sh | sed 's/^/      /'
  echo
  rm -f "$LOG"
  ./scripts/deploy-ensaio.sh "$IMG_ENSAIO_TAG" > "$LOG" 2>&1
  RC2=$?
fi

mostrar_log "$LOG"
echo
echo "  codigo de saida: $RC2   (esperado: 1 = deploy reprovado, rollback OK)"
if [ "$RC2" = "1" ] && grep -q "ROLLBACK OK" "$LOG" && [ "$(saude)" = "healthy" ]; then
  RES2="OK — health-gate reprovou e o rollback automatico devolveu o servico"
else
  RES2="FALHOU — rc=$RC2, saude=$(saude), 'ROLLBACK OK' no log? $(grep -q 'ROLLBACK OK' "$LOG" && echo sim || echo nao)"
fi
echo "  $RES2"
echo "  BACKEND_TAG apos o rollback: $(env_get BACKEND_TAG)   (esperado: previous)"

# =============================================================================
echo
echo "============================================================"
echo " ATO 3 — rollback.sh manual"
echo "============================================================"
# =============================================================================
echo "  3a) sem argumento: deve LISTAR as versoes disponiveis"
echo
rm -f "$LOG"
./scripts/rollback.sh < /dev/null > "$LOG" 2>&1
mostrar_log "$LOG"
# A listagem so vale se trouxer versao de verdade: o cabecalho sozinho seria
# 'verde que nao prova nada'. A tag boa esta local, entao tem que aparecer.
LISTOU=0
grep -q "Imagens disponiveis" "$LOG" && grep -q "$TAG_BOA" "$LOG" && LISTOU=1

echo
echo "  3b) voltando para a tag boa: ${TAG_BOA}"
echo
rm -f "$LOG"
./scripts/rollback.sh "$TAG_BOA" < /dev/null > "$LOG" 2>&1
RC3=$?
mostrar_log "$LOG"
echo
echo "  codigo de saida: $RC3   (esperado: 0)"
if [ "$RC3" = "0" ] && [ "$LISTOU" -ge 1 ] && [ "$(env_get BACKEND_TAG)" = "$TAG_BOA" ] && [ "$(saude)" = "healthy" ]; then
  RES3="OK — listou as versoes e voltou para ${TAG_BOA}"
else
  RES3="FALHOU — rc=$RC3, listagem=$LISTOU, tag=$(env_get BACKEND_TAG), saude=$(saude)"
fi
echo "  $RES3"

# =============================================================================
echo
echo "============================================================"
echo " RESUMO"
echo "============================================================"
echo " ATO 1 (tag inexistente)   $RES1"
echo " ATO 2 (imagem quebrada)   $RES2"
echo " ATO 3 (rollback manual)   $RES3"
echo
echo " site por dentro do nginx: HTTP $(curl -s -o /dev/null -w '%{http_code}' http://localhost/nginx-health)"

case "$RES1$RES2$RES3" in
  *FALHOU*) exit 1 ;;
  *)        exit 0 ;;
esac
