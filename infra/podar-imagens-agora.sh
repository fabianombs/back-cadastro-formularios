#!/usr/bin/env bash
# =============================================================================
# podar-imagens-agora.sh                                      (FABIANO-81)
#
# Roda a poda UMA VEZ, agora, sem esperar o proximo deploy.
#
# A correcao vive no deploy-safe.sh e so age quando alguem faz deploy. As 20
# tags que ja estao acumuladas na maquina (1,874 GB) ficariam la ate la. Este
# script existe para cobrar essa divida hoje — e para produzir o "depois" que o
# criterio do card pede.
#
# Ele NAO tem copia da funcao: extrai a mesma podar_imagens() do deploy-safe.sh.
# Uma copia divergiria em silencio na primeira vez que alguem mexesse em um dos
# dois arquivos.
#
# Roda em producao ou em homologacao.
# =============================================================================
set -uo pipefail

DIR_DEPLOY="${DIR_DEPLOY:-/home/ec2-user/fabiano/deploy}"
ALVO="${ALVO:-${DIR_DEPLOY}/scripts/deploy-safe.sh}"
CONTAINER="fabiano-backend"

cd "$DIR_DEPLOY" || { echo "ERRO: $DIR_DEPLOY nao existe"; exit 1; }
env_get() { grep -E "^${1}=" .env 2>/dev/null | head -1 | cut -d= -f2-; }

GHCR_OWNER=$(env_get GHCR_OWNER)
[ -n "$GHCR_OWNER" ] || { echo "ERRO: GHCR_OWNER ausente no .env"; exit 1; }
IMAGEM="ghcr.io/${GHCR_OWNER}/fabiano-back"

# A tag que esta no ar e protegida como se fosse a TAG_NOVA de um deploy.
TAG_NOVA=$(env_get BACKEND_TAG)
TAG_ANTERIOR="previous"

if env_get DB_HOST | grep -q homolog; then AMBIENTE="HOMOLOGACAO"; else AMBIENTE="PRODUCAO"; fi

echo "============================================================"
echo " poda de imagens — $AMBIENTE"
echo " maquina: $(hostname)   $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M:%S')"
echo " imagem:  $IMAGEM"
echo " no ar:   ${TAG_NOVA:-<sem BACKEND_TAG no .env>}"
echo "============================================================"

[ -f "$ALVO" ] || { echo "ERRO: nao achei $ALVO"; exit 1; }
eval "$(sed -n '/^podar_imagens() {/,/^}/p' "$ALVO")"
KEEP_IMAGENS=$(grep -E '^KEEP_IMAGENS=' "$ALVO" | head -1 | cut -d= -f2)
command -v podar_imagens >/dev/null \
  || { echo "ERRO: podar_imagens nao existe no $ALVO — a correcao nao chegou nesta maquina."; exit 1; }
echo "funcao extraida do deploy-safe.sh   (KEEP_IMAGENS=${KEEP_IMAGENS})"

echo
echo "=== ANTES ==="
docker system df | sed 's/^/  /'
df -h / | sed 's/^/  /'
echo "  tags do backend: $(docker images "$IMAGEM" --format '{{.Tag}}' | wc -l)"

echo
echo "=== podando ==="
podar_imagens

echo
echo "=== DEPOIS ==="
docker system df | sed 's/^/  /'
df -h / | sed 's/^/  /'
echo "  tags do backend: $(docker images "$IMAGEM" --format '{{.Tag}}' | wc -l)"

echo
echo "=== prova de que a rede do rollback continua la ==="
# Se esta linha nao imprimir nada, o rollback perdeu a rede e a poda tem bug.
docker images "${IMAGEM}:previous" --format '  previous -> {{.ID}}  ({{.Size}}, criada {{.CreatedSince}})'
echo "  container rodando: $(docker inspect --format='{{.Image}}' "$CONTAINER" 2>/dev/null | cut -c1-20)..."
echo "  saude: $(docker inspect --format='{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null || echo ausente)"
