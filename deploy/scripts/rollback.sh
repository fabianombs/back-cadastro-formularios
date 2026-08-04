#!/usr/bin/env bash
# =============================================================================
# rollback.sh [tag] [--com-banco]              (FABIANO-18)
#
# Rollback MANUAL, para o caso que o auto-rollback nao cobre: o deploy subiu
# saudavel, passou no health-gate, e mesmo assim esta errado. Bug que so
# aparece em producao, comportamento que nenhum healthcheck pega.
#
# Uso:
#   ./scripts/rollback.sh                    interativo, lista o que da para voltar
#   ./scripts/rollback.sh 4a7b1c9            volta para uma tag especifica
#   ./scripts/rollback.sh previous           volta para a imagem anterior (local)
#   ./scripts/rollback.sh 4a7b1c9 --com-banco   TAMBEM restaura o banco (perigoso)
#
# DE ONDE VEM CADA VERSAO
#   :previous   tag LOCAL, criada pelo deploy-safe.sh antes de cada deploy.
#               E a rede de seguranca que funciona mesmo com o GHCR fora do ar.
#   <sha curto> tag no GHCR, uma por commit publicado. E o historico de verdade:
#               da para voltar para qualquer versao ja publicada, nao so a
#               anterior. E o que torna este script util no caso "subiu bem mas
#               esta errado", que pode ser descoberto tres deploys depois.
# =============================================================================
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

ENV_FILE=".env"
COMPOSE="docker compose"
SERVICO="backend"
CONTAINER="fabiano-backend"
CONTAINER_NGINX="fabiano-nginx"
BACKUPS="/app/backups/pre-deploy"
HEALTH_TIMEOUT=120

env_get() {
  grep -E "^${1}=" "$ENV_FILE" 2>/dev/null | head -1 | cut -d= -f2-
}

[ -f "$ENV_FILE" ] || { echo "ERRO: $ENV_FILE nao encontrado em $(pwd)"; exit 1; }

GHCR_OWNER=$(env_get GHCR_OWNER)
[ -n "$GHCR_OWNER" ] || { echo "ERRO: GHCR_OWNER ausente em $ENV_FILE"; exit 1; }

IMAGEM="ghcr.io/${GHCR_OWNER}/fabiano-back"
TAG_ATUAL=$(env_get BACKEND_TAG)

ALVO="${1:-}"
COM_BANCO="${2:-}"

# =============================================================================
# Modo interativo
# =============================================================================
if [ -z "$ALVO" ]; then
  echo "Rodando agora: ${IMAGEM}:${TAG_ATUAL:-?}"
  echo
  echo "Imagens disponiveis LOCALMENTE (rollback imediato, sem rede):"
  docker images "$IMAGEM" --format '{{.Tag}}\t{{.CreatedSince}}\t{{.Size}}' 2>/dev/null \
    | sort \
    | awk -F'\t' '{ printf "   %-12s  %-18s  %s\n", $1, $2, $3 }'
  echo
  echo "Qualquer tag publicada no GHCR tambem serve — digite o SHA curto do commit."
  echo "Historico: https://github.com/${GHCR_OWNER}?tab=packages"
  echo
  if [ ! -t 0 ]; then
    echo "ERRO: sem terminal e sem tag informada. Passe a tag como argumento."
    exit 1
  fi
  read -r -p "Tag para voltar (vazio cancela): " ALVO
  [ -n "$ALVO" ] || { echo "Cancelado."; exit 0; }
fi

if [ "$ALVO" = "$TAG_ATUAL" ]; then
  echo "AVISO: '$ALVO' e a tag que ja esta rodando. Nada a fazer."
  exit 0
fi

# =============================================================================
# A imagem existe? Local ou no registry.
# =============================================================================
LOCAL=0
if docker image inspect "${IMAGEM}:${ALVO}" >/dev/null 2>&1; then
  LOCAL=1
  echo "==> ${IMAGEM}:${ALVO} ja esta na maquina"
else
  echo "==> ${IMAGEM}:${ALVO} nao esta local, puxando do GHCR..."
  if ! docker pull "${IMAGEM}:${ALVO}"; then
    echo
    echo "ERRO: nao consegui puxar ${IMAGEM}:${ALVO}."
    echo "      Confira a tag, ou faca 'docker login ghcr.io' se for autenticacao."
    echo "      NADA foi alterado — o sistema continua no ar como estava."
    exit 1
  fi
fi

# =============================================================================
# Confirmacao
# =============================================================================
# Friccao BAIXA para voltar a imagem: rollback e acao de recuperacao, e travar
# quem esta apagando incendio e pior do que o incendio.
echo
echo "  de:   ${TAG_ATUAL:-?}"
echo "  para: ${ALVO}"

# Sem terminal (rodando pelo GitHub Actions, por exemplo) nao ha quem responda.
# O 'read' receberia EOF, cairia no ramo de cancelar e o script sairia com 0 —
# o botao pareceria ter funcionado sem ter feito nada. Quem disparou pelo
# workflow ja confirmou ao digitar a tag e clicar em Run.
if [ -t 0 ]; then
  read -r -p "Confirma? [s/N] " OK
  case "$OK" in s|S|sim|SIM) ;; *) echo "Cancelado."; exit 0 ;; esac
else
  echo "  (sem terminal — confirmacao dispensada)"
fi

# =============================================================================
# Troca da imagem
# =============================================================================
echo "==> Subindo ${ALVO}"
export BACKEND_TAG="$ALVO"

# --pull never porque a imagem ja foi garantida acima, e porque o
# docker-compose.yml declara 'pull_policy: always' — sem este flag, uma tag que
# so existe localmente (como :previous) faria o compose tentar busca-la no
# registry e falhar justo no momento do resgate.
# --no-deps para nao recriar o nginx junto: ele nao precisa reiniciar, so
# recarregar.
$COMPOSE up -d --no-deps --force-recreate --pull never "$SERVICO"

echo "==> Aguardando ficar saudavel (ate ${HEALTH_TIMEOUT}s)"
ok=0
for i in $(seq 1 "$HEALTH_TIMEOUT"); do
  ESTADO=$(docker inspect --format='{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null || echo "ausente")
  case "$ESTADO" in
    healthy)   ok=1; break ;;
    unhealthy) echo "    container reportou unhealthy em ${i}s"; break ;;
  esac
  sleep 1
done

if [ "$ok" != "1" ]; then
  echo
  echo "ERRO: a versao ${ALVO} tambem nao ficou saudavel."
  echo "      Ultimos 50 logs:"
  docker logs --tail 50 "$CONTAINER" 2>&1 | sed 's/^/        /'
  echo
  echo "      Tente outra tag, ou investigue antes de continuar."
  exit 1
fi

# O .env passa a refletir o que esta de fato rodando — senao um reboot da
# maquina traria de volta a versao ruim.
if grep -q '^BACKEND_TAG=' "$ENV_FILE"; then
  sed -i "s|^BACKEND_TAG=.*|BACKEND_TAG=${ALVO}|" "$ENV_FILE"
else
  echo "BACKEND_TAG=${ALVO}" >> "$ENV_FILE"
fi

# O nginx guarda o IP do container resolvido uma vez. Recriar o backend muda o
# IP, e sem reload o nginx bate no endereco velho ate reiniciar sozinho.
if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NGINX}$"; then
  docker exec "$CONTAINER_NGINX" nginx -s reload 2>/dev/null \
    && echo "    nginx recarregado" \
    || echo "    AVISO: nao consegui recarregar o nginx"
fi

echo
echo "ROLLBACK OK — ${ALVO} no ar."

# =============================================================================
# Restauracao do banco — separada, e de proposito dificil
# =============================================================================
if [ "$COM_BANCO" != "--com-banco" ]; then
  echo
  echo "O BANCO NAO FOI TOCADO."
  echo
  echo "Voltar a imagem resolve bug de aplicacao. NAO resolve migration que"
  echo "destruiu dado — o Flyway so anda para frente, e a versao antiga da"
  echo "aplicacao vai encontrar o schema novo."
  echo
  echo "Se a suspeita for de estrago no banco, rode com --com-banco. Antes,"
  echo "leia o que ele avisa: normalmente corrigir para frente custa menos."
  exit 0
fi

echo
echo "============================================================"
echo "  RESTAURACAO DO BANCO"
echo "============================================================"

DUMP=$(ls -1t "$BACKUPS"/db_before_*.sql.gz 2>/dev/null | head -1)
if [ -z "$DUMP" ]; then
  echo "ERRO: nenhum dump pre-deploy encontrado em $BACKUPS"
  exit 1
fi

DB_HOST=$(env_get DB_HOST)
DB_PORT=$(env_get DB_PORT); [ -z "$DB_PORT" ] && DB_PORT=3306
DB_NAME=$(env_get DB_NAME)
DB_USER=$(env_get DB_USER)
DB_PASSWORD=$(env_get DB_PASSWORD)

echo "  dump:  $DUMP"
echo "  feito: $(stat -c '%y' "$DUMP" 2>/dev/null | cut -d. -f1)"
echo
echo "  TUDO que foi gravado no banco depois desse horario sera PERDIDO."
echo "  Formularios respondidos, presencas marcadas, agendamentos, usuarios."
echo
echo "  Antes de continuar, meca o tamanho do estrago:"
echo "    mysql -h $DB_HOST -u $DB_USER -p $DB_NAME -e \\"
echo "      \"SELECT COUNT(*) FROM form_submissions WHERE created_at > '<horario-do-dump>';\""
echo

# Friccao ALTA aqui, ao contrario da troca de imagem: esta e a unica operacao
# do projeto que DESTROI dado do cliente de forma irreversivel. Digitar a
# palavra inteira obriga a ler a frase antes.
#
# Sem terminal, a palavra vem por variavel de ambiente. E deliberado que o
# workflow precise definir CONFIRMO_PERDA_DE_DADOS=RESTAURAR explicitamente:
# marcar uma caixa por engano nao pode ser suficiente para apagar dado.
if [ -t 0 ]; then
  read -r -p "  Digite RESTAURAR para confirmar: " CONFIRMA
else
  CONFIRMA="${CONFIRMO_PERDA_DE_DADOS:-}"
  echo "  (sem terminal — lendo CONFIRMO_PERDA_DE_DADOS)"
fi
[ "$CONFIRMA" = "RESTAURAR" ] || { echo "  Cancelado. Banco intacto."; exit 0; }

# Rede antes da rede: um dump do estado ATUAL, para poder desfazer a restauracao.
SEGURANCA="$BACKUPS/db_safety_$(TZ=America/Sao_Paulo date +%Y%m%d-%H%M%S).sql.gz"
echo "  gravando dump de seguranca do estado atual..."
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" \
  --single-transaction --set-gtid-purged=OFF --routines --triggers "$DB_NAME" \
  2>/tmp/safety.err | gzip -9 > "$SEGURANCA"
if [ "${PIPESTATUS[0]}" -ne 0 ]; then
  echo "  ERRO: nao consegui gravar o dump de seguranca. Restauracao ABORTADA."
  tail -3 /tmp/safety.err 2>/dev/null
  rm -f "$SEGURANCA"
  exit 1
fi
echo "  seguranca em: $SEGURANCA"

echo "  restaurando..."
if gunzip -c "$DUMP" | mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME"; then
  echo "  banco restaurado."
  echo "  reiniciando a aplicacao para limpar cache e pool de conexao..."
  $COMPOSE restart "$SERVICO"
  echo
  echo "  Para desfazer esta restauracao:"
  echo "    gunzip -c $SEGURANCA | mysql -h $DB_HOST -u $DB_USER -p $DB_NAME"
else
  echo "  ERRO na restauracao. O banco pode estar em estado inconsistente."
  echo "  Dump de seguranca do estado anterior: $SEGURANCA"
  exit 1
fi
