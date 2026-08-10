#!/usr/bin/env bash
# =============================================================================
# deploy-safe.sh <tag-da-imagem>        (FABIANO-17)
#
# Deploy do backend em container, com as mesmas garantias da versao systemd:
# falha-segura, health-gate e rollback automatico.
#
# Uso:  ./scripts/deploy-safe.sh 4a7b1c9
#       ./scripts/deploy-safe.sh latest
#
# O QUE FOI PRESERVADO da versao anterior (nao mexer sem entender):
#   * GUARDA 0 — as ferramentas existem? Foi a ausencia desta guarda que
#     permitiu meses de deploy sem backup nenhum (FABIANO-29).
#   * GUARDA 1 — sem credenciais, aborta ANTES de encostar no servico.
#   * GUARDA 2 — dump vazio, truncado ou sem tabela aborta ANTES de encostar
#     no servico. Um gzip vazio tem 20 bytes e passa em `[ -s arquivo ]`.
#   * analisar_banco() — depois de uma falha, responde a unica pergunta que
#     decide se vale restaurar o dump: alguma migration rodou, e ela destruiu
#     alguma coisa?
#   * Restauracao do banco NUNCA e automatica. O script diagnostica; a decisao
#     e humana.
#
# O QUE MUDOU por causa do Docker:
#   * Credenciais vem do deploy/.env, nao mais do ambiente do systemd.
#   * Rollback e por TAG DE IMAGEM, nao por copia de JAR.
#   * O health-gate le o status do container, nao um curl no host — o backend
#     nao publica porta nenhuma (ver docker-compose.yml).
#   * As migrations sao lidas de dentro da IMAGEM, nao de dentro do JAR.
#
# Exit codes (padrao do projeto):
#   0  deploy OK
#   1  deploy falhou, rollback OK          -> investigar logs
#   2  backup falhou, deploy abortado      -> PRODUCAO INTACTA
#   3  deploy e rollback falharam          -> urgencia manual
# =============================================================================
set -uo pipefail

TAG_NOVA="${1:?Use: deploy-safe.sh <tag-da-imagem>   ex: deploy-safe.sh 4a7b1c9}"

cd "$(dirname "$0")/.." || exit 2

ENV_FILE=".env"
COMPOSE="docker compose"
SERVICO="backend"
CONTAINER="fabiano-backend"
CONTAINER_NGINX="fabiano-nginx"
BACKUPS="/app/backups/pre-deploy"
HEALTH_TIMEOUT=120
KEEP=8
# Quantas versoes do backend ficam guardadas na maquina, ALEM das protegidas
# (:previous, :latest e a tag do deploy corrente). Cada imagem pesa ~380 MB.
KEEP_IMAGENS=5

# Um gzip VAZIO tem exatamente 20 bytes — foi assim que a falha de 2026 passou
# batida por cinco deploys seguidos.
MIN_BACKUP_BYTES=10240

PADRAO_DESTRUTIVO='DROP[[:space:]]+(TABLE|COLUMN|DATABASE)|TRUNCATE|DELETE[[:space:]]+FROM|MODIFY[[:space:]]+COLUMN|CHANGE[[:space:]]+COLUMN'

TS=$(TZ=America/Sao_Paulo date +%Y%m%d-%H%M%S)
mkdir -p "$BACKUPS"

# --- Leitura do .env ---------------------------------------------------------
# Parser proprio, nunca `source`: valor com espaco quebra o shell.
# EMAIL_FROM_NAME=Hub Cavlovers vira tentativa de executar 'Cavlovers'.
env_get() {
  grep -E "^${1}=" "$ENV_FILE" 2>/dev/null | head -1 | cut -d= -f2-
}

# =============================================================================
# GUARDA 0 — as ferramentas existem?
# =============================================================================
for BIN in mysqldump mysql docker; do
  if ! command -v "$BIN" >/dev/null 2>&1; then
    echo "ERRO: '$BIN' nao encontrado nesta maquina."
    [ "$BIN" = "docker" ] || echo "      Instale com: sudo yum install -y mariadb"
    echo "      Deploy abortado, PROD INTACTO."
    exit 2
  fi
done

if [ ! -f "$ENV_FILE" ]; then
  echo "ERRO: $ENV_FILE nao encontrado em $(pwd). Deploy abortado, PROD INTACTO."
  exit 2
fi

# =============================================================================
# GUARDA 1 — credenciais do banco
# =============================================================================
DB_HOST=$(env_get DB_HOST)
DB_PORT=$(env_get DB_PORT); [ -z "$DB_PORT" ] && DB_PORT=3306
DB_NAME=$(env_get DB_NAME)
DB_USER=$(env_get DB_USER)
DB_PASSWORD=$(env_get DB_PASSWORD)
GHCR_OWNER=$(env_get GHCR_OWNER)

if [ -z "$DB_HOST" ] || [ -z "$DB_NAME" ] || [ -z "$DB_USER" ] || [ -z "$DB_PASSWORD" ]; then
  echo "ERRO: DB_HOST/DB_NAME/DB_USER/DB_PASSWORD ausentes em $ENV_FILE."
  echo "      Deploy abortado, PROD INTACTO."
  exit 2
fi
if [ -z "$GHCR_OWNER" ]; then
  echo "ERRO: GHCR_OWNER ausente em $ENV_FILE. Deploy abortado, PROD INTACTO."
  exit 2
fi

IMAGEM="ghcr.io/${GHCR_OWNER}/fabiano-back"
# A senha vai por MYSQL_PWD, nunca por -p<senha>. Com -p ela fica visivel no
# 'ps' para qualquer usuario da maquina e — pior — o analisar_banco ECOA este
# comando na tela quando encontra migration destrutiva. A senha iria parar no
# log do deploy exatamente no momento em que alguem cola esse log pedindo
# ajuda. Mesmo padrao ja usado na anonimizacao do subir-homolog.sh.
export MYSQL_PWD="$DB_PASSWORD"
MYSQL_Q="mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -N -B $DB_NAME"
DB_BACKUP="$BACKUPS/db_before_${TAG_NOVA}_${TS}.sql.gz"

# =============================================================================
# PODA DE IMAGENS
# =============================================================================
# POR QUE A LINHA ANTIGA NUNCA REMOVEU NADA
#
#   docker image prune -f --filter "until=168h"
#
# Sem '-a', o prune so remove imagem DANGLING — sem nenhuma tag. Toda imagem do
# backend chega tagueada com o SHA do commit, entao nenhuma delas jamais foi
# candidata. Medido em producao em 10/08/2026: 20 tags acumuladas, 1,874 GB
# recuperaveis, e o comando devolveu "Total reclaimed space: 0B".
#
# E '-a' tambem nao serve: removeria toda imagem sem container, o que inclui a
# :previous — a rede do rollback. Por isso a poda e por TAG, com lista explicita
# de protegidas. Harness em infra/testar-podar-imagens.sh (FABIANO-81).
podar_imagens() {
  local em_uso protegidas tag id candidatas removidas=0

  # Comparar por ID e nao por nome: a mesma imagem costuma ter duas tags (o SHA
  # do commit e a :previous), e o nome sozinho nao diz qual esta em uso.
  em_uso=$(docker inspect --format='{{.Image}}' "$CONTAINER" 2>/dev/null || echo "")

  # 'previous' e a rede do rollback e 'latest' e o ponteiro movel do registry:
  # nenhuma das duas sai, em circunstancia nenhuma. As tags do deploy corrente
  # entram na lista para que a poda no caminho de FALHA nao apague a imagem
  # quebrada antes de alguem conseguir olhar para ela.
  protegidas=" previous latest ${TAG_NOVA:-} ${TAG_ANTERIOR:-} "

  # O filtro de protegidas vem ANTES do tail de proposito. Se viesse depois, a
  # :previous ocuparia uma das KEEP_IMAGENS vagas e a maquina guardaria uma
  # versao a menos do que o numero diz.
  candidatas=$(
    docker images "$IMAGEM" --format '{{.Tag}}' 2>/dev/null \
      | grep -v '^<none>$' \
      | grep -vxF -e previous -e latest -e "${TAG_NOVA:-@}" -e "${TAG_ANTERIOR:-@}" \
      | tail -n +$((KEEP_IMAGENS + 1))
  )

  for tag in $candidatas; do
    # Cinto e suspensorio: se um dia o filtro acima for mexido, esta conferencia
    # impede que uma tag protegida chegue no rmi.
    case "$protegidas" in *" $tag "*) continue ;; esac

    id=$(docker images --no-trunc --quiet "${IMAGEM}:${tag}" 2>/dev/null | head -1)
    [ -n "$id" ] && [ "$id" = "$em_uso" ] && continue

    if docker rmi "${IMAGEM}:${tag}" >/dev/null 2>&1; then
      removidas=$((removidas + 1))
      echo "      removida ${IMAGEM}:${tag}"
    fi
  done

  # Destaguear deixa as camadas orfas. AGORA o prune sem '-a' tem o que fazer:
  # e exatamente para camada dangling que ele existe.
  docker image prune -f >/dev/null 2>&1

  echo "    imagens do backend removidas: ${removidas}  (mantidas as ${KEEP_IMAGENS} mais novas + previous)"
  echo "    disco: $(df -h / | awk 'NR==2{print $4" livres de "$2", "$5" usado"}')"
}

# =============================================================================
# ANALISE DO BANCO APOS FALHA
# =============================================================================
# Responde a unica pergunta que decide se vale restaurar o dump:
# "alguma migration rodou neste deploy, e ela destruiu alguma coisa?"
analisar_banco() {
  local rank_antes="$1"
  echo
  echo "==> ANALISE DO BANCO"

  local rank_depois
  rank_depois=$($MYSQL_Q -e "SELECT COALESCE(MAX(installed_rank),0) FROM flyway_schema_history;" 2>/dev/null || echo "?")

  if [ "$rank_depois" = "?" ]; then
    echo "    Nao consegui consultar o flyway_schema_history."
    echo "    Verifique manualmente antes de decidir sobre restauracao."
    return
  fi

  if [ "$rank_depois" = "$rank_antes" ]; then
    echo "    NENHUMA MIGRATION RODOU — banco intacto."
    echo "    NAO restaure o dump. O problema esta na aplicacao, nao no banco."
    echo "    Corrija o bug e faca um novo deploy."
    return
  fi

  echo "    ATENCAO: o schema MUDOU neste deploy (rank $rank_antes -> $rank_depois)."
  echo
  echo "    Migrations aplicadas:"
  $MYSQL_Q -e "
    SELECT CONCAT('      V', version, ' — ', description,
                  CASE WHEN success = 1 THEN '  [ok]' ELSE '  [FALHOU]' END)
    FROM flyway_schema_history
    WHERE installed_rank > $rank_antes
    ORDER BY installed_rank;" 2>/dev/null

  local versoes destrutivo=0
  versoes=$($MYSQL_Q -e "
    SELECT version FROM flyway_schema_history
    WHERE installed_rank > $rank_antes;" 2>/dev/null)

  echo
  echo "    Conteudo das migrations:"
  for v in $versoes; do
    local sql
    # As migrations vivem DENTRO DA IMAGEM. O Dockerfile explode o jar em
    # camadas com layertools, entao o caminho e /app/BOOT-INF/classes/...
    # --entrypoint sh porque o entrypoint padrao sobe a aplicacao inteira.
    sql=$(docker run --rm --entrypoint sh "${IMAGEM}:${TAG_NOVA}" \
            -c "cat /app/BOOT-INF/classes/db/migration/V${v}__*.sql" 2>/dev/null)
    if [ -z "$sql" ]; then
      echo "      V${v}: nao consegui ler o SQL da imagem — verifique manualmente"
      destrutivo=1
      continue
    fi
    if echo "$sql" | grep -qiE "$PADRAO_DESTRUTIVO"; then
      echo "      V${v}: *** CONTEM COMANDO DESTRUTIVO ***"
      echo "$sql" | grep -inE "$PADRAO_DESTRUTIVO" | sed 's/^/          /'
      destrutivo=1
    else
      echo "      V${v}: apenas adicoes (sem comando destrutivo)"
    fi
  done

  echo
  if [ "$destrutivo" -eq 0 ]; then
    echo "    VEREDITO: as migrations so ADICIONARAM estrutura."
    echo "    NAO precisa restaurar. A versao antiga da aplicacao ignora coluna"
    echo "    ou tabela nova sem problema, e o proximo deploy ja as encontra prontas."
  else
    echo "    VEREDITO: houve comando DESTRUTIVO. Avalie antes de agir."
    echo
    echo "    Antes de restaurar, meca o que voce perderia:"
    echo "      $MYSQL_Q -e \"SELECT COUNT(*) FROM form_submissions WHERE created_at > '<inicio-do-deploy>';\""
    echo
    echo "    Opcao A (preferida) — corrigir para frente:"
    echo "      migration nova que recria o que foi removido, repopulando so"
    echo "      aquilo a partir do dump. Preserva todo o resto."
    echo
    echo "    Opcao B — restaurar o dump inteiro:"
    echo "      gunzip -c $DB_BACKUP | mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p $DB_NAME"
    echo "      DESCARTA tudo que foi gravado desde o inicio deste deploy."
  fi
}

# =============================================================================
echo "==> [1/5] Backup do banco (antes de qualquer migration)"
# =============================================================================

# Base de comparacao para saber, depois, se alguma migration rodou.
FLYWAY_ANTES=$($MYSQL_Q -e "SELECT COALESCE(MAX(installed_rank),0) FROM flyway_schema_history;" 2>/dev/null || echo "0")

# PIPESTATUS[0] captura o exit code do mysqldump, nao o do gzip. Sem isso, um
# mysqldump que falha some atras de um gzip que "deu certo".
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" \
  --single-transaction --set-gtid-purged=OFF --routines --triggers "$DB_NAME" \
  2>/tmp/dump.err | gzip -9 > "$DB_BACKUP"
DUMP_RC=${PIPESTATUS[0]}

if [ "$DUMP_RC" -ne 0 ]; then
  echo "ERRO: mysqldump retornou codigo $DUMP_RC. Deploy abortado, PROD INTACTO."
  tail -5 /tmp/dump.err 2>/dev/null
  rm -f "$DB_BACKUP"
  exit 2
fi

BACKUP_BYTES=$(stat -c%s "$DB_BACKUP" 2>/dev/null || echo 0)
if [ "$BACKUP_BYTES" -lt "$MIN_BACKUP_BYTES" ]; then
  echo "ERRO: backup com apenas ${BACKUP_BYTES} bytes (minimo ${MIN_BACKUP_BYTES})."
  echo "      Dump vazio ou truncado. Deploy abortado, PROD INTACTO."
  tail -5 /tmp/dump.err 2>/dev/null
  rm -f "$DB_BACKUP"
  exit 2
fi

# "Dump completed on <data>" so aparece quando o mysqldump termina de verdade.
# E a unica prova real de que o arquivo serve para restaurar.
if ! gunzip -c "$DB_BACKUP" 2>/dev/null | tail -5 | grep -q "Dump completed"; then
  echo "ERRO: dump sem a marca 'Dump completed' — incompleto ou corrompido."
  echo "      Deploy abortado, PROD INTACTO."
  rm -f "$DB_BACKUP"
  exit 2
fi

TABELAS=$(gunzip -c "$DB_BACKUP" 2>/dev/null | grep -c "CREATE TABLE" || echo 0)
if [ "$TABELAS" -lt 1 ]; then
  echo "ERRO: backup sem nenhum CREATE TABLE. Deploy abortado, PROD INTACTO."
  rm -f "$DB_BACKUP"
  exit 2
fi

echo "    backup OK — $(numfmt --to=iec "$BACKUP_BYTES" 2>/dev/null || echo "${BACKUP_BYTES}B"), ${TABELAS} tabelas, flyway rank ${FLYWAY_ANTES}"

# =============================================================================
echo "==> [2/5] Marcando a imagem atual para rollback"
# =============================================================================

# Guarda o ID da imagem que esta rodando AGORA e a marca como :previous.
# E o equivalente ao _previous.jar da versao anterior — rollback instantaneo,
# sem depender do registry estar acessivel na hora do desespero.
TAG_ANTERIOR=""
ID_ATUAL=$(docker inspect --format='{{.Image}}' "$CONTAINER" 2>/dev/null || echo "")

if [ -n "$ID_ATUAL" ]; then
  docker tag "$ID_ATUAL" "${IMAGEM}:previous" 2>/dev/null && TAG_ANTERIOR="previous"
  echo "    imagem atual marcada como ${IMAGEM}:previous"
else
  # Primeiro deploy: nao existe container rodando. Nao e erro.
  echo "    nenhum container em execucao — primeiro deploy, sem rollback disponivel"
fi

# =============================================================================
echo "==> [3/5] Puxando e subindo a tag $TAG_NOVA"
# =============================================================================

export BACKEND_TAG="$TAG_NOVA"

if ! $COMPOSE pull "$SERVICO"; then
  echo "ERRO: falhou ao puxar ${IMAGEM}:${TAG_NOVA}."
  echo "      Deploy abortado, PROD INTACTO (o container antigo continua no ar)."
  exit 2
fi

$COMPOSE up -d --no-deps --force-recreate "$SERVICO"

# =============================================================================
echo "==> [4/5] Health-gate (ate ${HEALTH_TIMEOUT}s)"
# =============================================================================

# Le o status do HEALTHCHECK do container, e nao um curl no host: o backend
# NAO publica porta nenhuma (ver docker-compose.yml), entao nao ha o que
# consultar de fora. O healthcheck do Dockerfile tem start_period de 90s.
ok=0
for i in $(seq 1 "$HEALTH_TIMEOUT"); do
  ESTADO=$(docker inspect --format='{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null || echo "ausente")
  case "$ESTADO" in
    healthy)   ok=1; break ;;
    unhealthy) echo "    container reportou unhealthy em ${i}s"; break ;;
  esac
  sleep 1
done

if [ "$ok" = "1" ]; then
  echo "==> [5/5] OK — tag $TAG_NOVA no ar."

  # Persiste a tag no .env para que um 'docker compose up' feito a mao, ou um
  # reboot da maquina, subam a mesma versao que esta rodando agora.
  if grep -q '^BACKEND_TAG=' "$ENV_FILE"; then
    sed -i "s|^BACKEND_TAG=.*|BACKEND_TAG=${TAG_NOVA}|" "$ENV_FILE"
  else
    echo "BACKEND_TAG=${TAG_NOVA}" >> "$ENV_FILE"
  fi

  # Reload graceful do nginx: ele resolve o nome 'backend' uma vez e guarda o
  # IP. Recriar o container muda o IP, e sem reload o nginx fica batendo no
  # endereco velho ate reiniciar. Reload leva ~50ms e nao derruba conexao.
  if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NGINX}$"; then
    docker exec "$CONTAINER_NGINX" nginx -s reload 2>/dev/null \
      && echo "    nginx recarregado" \
      || echo "    AVISO: nao consegui recarregar o nginx"
  fi

  # Cleanup TOLERANTE: daqui pra frente nada pode derrubar o script.
  # Sem isso, um grep sem match no primeiro deploy mata o script DEPOIS de um
  # deploy bem-sucedido, e o CI marca falso positivo de falha.
  set +e
  set +o pipefail
  ls -1t "$BACKUPS"/db_before_*.gz 2>/dev/null | tail -n +$((KEEP+1)) | xargs -r rm -f
  podar_imagens
  exit 0
fi

# =============================================================================
echo "==> [5/5] FALHA — ROLLBACK automatico"
# =============================================================================
echo "    Ultimos 50 logs do container que falhou:"
docker logs --tail 50 "$CONTAINER" 2>&1 | sed 's/^/      /'

if [ -z "$TAG_ANTERIOR" ]; then
  echo
  echo "    NAO HA VERSAO ANTERIOR para voltar — este era o primeiro deploy."
  echo "    O container fica de pe para diagnostico: docker logs $CONTAINER"
  analisar_banco "$FLYWAY_ANTES"
  exit 3
fi

echo "    voltando para ${IMAGEM}:${TAG_ANTERIOR}"
export BACKEND_TAG="$TAG_ANTERIOR"

# --pull never e OBRIGATORIO aqui. O docker-compose.yml declara
# 'pull_policy: always', e a tag :previous so existe LOCALMENTE — tentar puxa-la
# do registry falharia justo no momento em que o rollback precisa funcionar.
$COMPOSE up -d --no-deps --force-recreate --pull never "$SERVICO"

ok=0
for i in $(seq 1 "$HEALTH_TIMEOUT"); do
  ESTADO=$(docker inspect --format='{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null || echo "ausente")
  [ "$ESTADO" = "healthy" ] && { ok=1; break; }
  sleep 1
done

if [ "$ok" = "1" ]; then
  echo "    ROLLBACK OK — versao anterior restaurada e no ar."
  if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NGINX}$"; then
    docker exec "$CONTAINER_NGINX" nginx -s reload 2>/dev/null || true
  fi
  # O .env volta a apontar para a versao que de fato esta rodando.
  sed -i "s|^BACKEND_TAG=.*|BACKEND_TAG=${TAG_ANTERIOR}|" "$ENV_FILE"
  # A poda tambem roda aqui. Antes ela so existia no caminho de sucesso, entao
  # uma sequencia de deploys que falham enchia o disco sem nunca limpar — e e
  # justamente quando o deploy vai mal que ninguem esta olhando para o disco.
  # A imagem quebrada de hoje (TAG_NOVA) e protegida: fica para diagnostico.
  podar_imagens
  analisar_banco "$FLYWAY_ANTES"
  exit 1
else
  echo "    CATASTROFE: rollback feito mas o container nao ficou healthy."
  echo "    Ver: docker logs $CONTAINER"
  analisar_banco "$FLYWAY_ANTES"
  exit 3
fi
