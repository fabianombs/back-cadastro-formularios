#!/usr/bin/env bash
# deploy-safe.sh <jar-novo> <versao>
# Deploy seguro: falha-segura (aborta antes de tocar no servico se o backup nao for possivel)
# + health-gate + rollback automatico. Compatível com systemd antigo (sem --value).
#
# CORRECAO 02/08/2026 (FABIANO-29):
#   A validacao antiga era `[ ! -s "$ARQUIVO" ]`, que so verifica "tem mais que
#   zero bytes". Com o mysqldump AUSENTE da EC2, o pipe entregava nada ao gzip,
#   que mesmo assim produzia um arquivo valido de 20 bytes — e a guarda passava.
#   Cinco deploys seguidos gravaram backups vazios sem ninguem perceber.
#   Agora a validacao confere: binario existe, exit code do mysqldump, tamanho
#   minimo real e a marca "Dump completed" dentro do arquivo.
#
#   A restauracao automatica do banco no rollback tambem foi removida: ela
#   descartava tudo gravado desde o inicio do deploy, mesmo quando o banco
#   estava intacto. No lugar, o script ANALISA o que aconteceu com o banco e
#   entrega o diagnostico pronto para a decisao humana.
set -uo pipefail

INCOMING_JAR="${1:?Use: deploy-safe.sh <jar-novo> <versao>}"
VERSION="${2:?informe a versao, ex: 20260612-1200-abc1234}"

SERVICE="poc-fabiano"
APP_JAR="/app/app.jar"
APP_USER="appuser"
RELEASES="/app/releases"
HEALTH_URL="http://localhost:8080/actuator/health"
HEALTH_TIMEOUT=90
KEEP=8

# Um gzip VAZIO tem exatamente 20 bytes — foi assim que a falha passou batida.
MIN_BACKUP_BYTES=10240

# Comandos que destroem dado. Usado para classificar as migrations que rodaram.
PADRAO_DESTRUTIVO='DROP[[:space:]]+(TABLE|COLUMN|DATABASE)|TRUNCATE|DELETE[[:space:]]+FROM|MODIFY[[:space:]]+COLUMN|CHANGE[[:space:]]+COLUMN'

mkdir -p "$RELEASES"

# --- GUARDA 0: as ferramentas existem? --------------------------------------
# Esta guarda nao existia, e foi a sua ausencia que permitiu meses de deploy
# sem backup nenhum.
for BIN in mysqldump mysql; do
  if ! command -v "$BIN" >/dev/null 2>&1; then
    echo "ERRO: '$BIN' nao encontrado nesta maquina."
    echo "      Instale com: sudo yum install -y mariadb"
    echo "      Deploy abortado, PROD INTACTO."
    exit 2
  fi
done

# --- Credenciais do banco do ambiente REAL do processo -----------------------
# Cobre Environment= e EnvironmentFile=. Sem 'systemctl --value' (systemd antigo).
PID=$(systemctl show -p MainPID "$SERVICE" 2>/dev/null | cut -d= -f2)
get() {
  local key="$1" v=""
  if [ -n "$PID" ] && [ "$PID" != "0" ] && [ -e "/proc/$PID/environ" ]; then
    v=$(sudo cat "/proc/$PID/environ" 2>/dev/null | tr '\0' '\n' | grep "^$key=" | head -1 | cut -d= -f2-)
  fi
  if [ -z "$v" ]; then
    v=$(systemctl show -p Environment "$SERVICE" 2>/dev/null | sed -n 's/^Environment=//p' | tr ' ' '\n' | grep "^$key=" | head -1 | cut -d= -f2-)
  fi
  echo "$v"
}
DB_HOST=$(get DB_HOST); DB_PORT=$(get DB_PORT); [ -z "$DB_PORT" ] && DB_PORT=3306
DB_NAME=$(get DB_NAME); DB_USER=$(get DB_USER); DB_PASSWORD=$(get DB_PASSWORD)

# GUARDA 1: sem credenciais nao da pra fazer backup -> aborta SEM mexer no servico
if [ -z "$DB_HOST" ] || [ -z "$DB_NAME" ] || [ -z "$DB_USER" ]; then
  echo "ERRO: nao consegui ler DB_HOST/DB_NAME/DB_USER do servico $SERVICE. Deploy abortado, PROD INTACTO."
  exit 2
fi

MYSQL_Q="mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p$DB_PASSWORD -N -B $DB_NAME"

PREV=$(cat /app/CURRENT_VERSION 2>/dev/null || echo "")
DB_BACKUP="$RELEASES/db_before_${VERSION}.sql.gz"

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

  # Chegou aqui: o schema mudou. Lista o que entrou e classifica.
  echo "    ATENCAO: o schema MUDOU neste deploy (rank $rank_antes -> $rank_depois)."
  echo
  echo "    Migrations aplicadas:"
  $MYSQL_Q -e "
    SELECT CONCAT('      V', version, ' — ', description,
                  CASE WHEN success = 1 THEN '  [ok]' ELSE '  [FALHOU]' END)
    FROM flyway_schema_history
    WHERE installed_rank > $rank_antes
    ORDER BY installed_rank;" 2>/dev/null

  # As migrations ficam dentro do JAR — da para ler o SQL delas e ver se algum
  # comando destroi dado. E a diferenca entre "adicionou uma coluna" (inofensivo
  # para a versao antiga da app) e "derrubou uma tabela" (precisa de decisao).
  local versoes destrutivo=0
  versoes=$($MYSQL_Q -e "
    SELECT version FROM flyway_schema_history
    WHERE installed_rank > $rank_antes;" 2>/dev/null)

  echo
  echo "    Conteudo das migrations:"
  for v in $versoes; do
    local sql
    sql=$(unzip -p "$INCOMING_JAR" "BOOT-INF/classes/db/migration/V${v}__*.sql" 2>/dev/null)
    if [ -z "$sql" ]; then
      echo "      V${v}: nao consegui ler o SQL do JAR — verifique manualmente"
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
    echo "      escreva uma migration nova que recria o que foi removido e"
    echo "      repopule so aquilo a partir do dump. Preserva todo o resto."
    echo
    echo "    Opcao B — restaurar o dump inteiro:"
    echo "      gunzip -c $DB_BACKUP | mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -p $DB_NAME"
    echo "      DESCARTA tudo que foi gravado desde o inicio deste deploy."
  fi
}

# =============================================================================
echo "==> [1/4] Backup (dump pre-migration + release $VERSION)"
# =============================================================================

# Guarda o estado do Flyway ANTES de qualquer coisa — e a base de comparacao
# que permite dizer depois se alguma migration rodou.
FLYWAY_ANTES=$($MYSQL_Q -e "SELECT COALESCE(MAX(installed_rank),0) FROM flyway_schema_history;" 2>/dev/null || echo "0")

# PIPESTATUS[0] captura o exit code do mysqldump, nao o do gzip.
# Sem isso, um mysqldump que falha some atras de um gzip que "deu certo".
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" \
  --single-transaction --routines --triggers "$DB_NAME" 2>/tmp/dump.err | gzip -9 > "$DB_BACKUP"
DUMP_RC=${PIPESTATUS[0]}

# GUARDA 2a: o mysqldump reclamou?
if [ "$DUMP_RC" -ne 0 ]; then
  echo "ERRO: mysqldump retornou codigo $DUMP_RC. Deploy abortado, PROD INTACTO."
  tail -5 /tmp/dump.err 2>/dev/null
  rm -f "$DB_BACKUP"
  exit 2
fi

# GUARDA 2b: o arquivo tem tamanho plausivel? (20 bytes = gzip vazio)
BACKUP_BYTES=$(stat -c%s "$DB_BACKUP" 2>/dev/null || echo 0)
if [ "$BACKUP_BYTES" -lt "$MIN_BACKUP_BYTES" ]; then
  echo "ERRO: backup com apenas ${BACKUP_BYTES} bytes (minimo ${MIN_BACKUP_BYTES})."
  echo "      Dump vazio ou truncado. Deploy abortado, PROD INTACTO."
  tail -5 /tmp/dump.err 2>/dev/null
  rm -f "$DB_BACKUP"
  exit 2
fi

# GUARDA 2c: o conteudo e mesmo um dump completo?
# "Dump completed on <data>" so aparece quando o mysqldump termina de verdade.
# E a unica prova real de que o arquivo serve para restaurar.
if ! gunzip -c "$DB_BACKUP" 2>/dev/null | tail -5 | grep -q "Dump completed"; then
  echo "ERRO: dump sem a marca 'Dump completed' — arquivo incompleto ou corrompido."
  echo "      Deploy abortado, PROD INTACTO."
  rm -f "$DB_BACKUP"
  exit 2
fi

# GUARDA 2d: tem tabela dentro?
TABELAS=$(gunzip -c "$DB_BACKUP" 2>/dev/null | grep -c "CREATE TABLE" || echo 0)
if [ "$TABELAS" -lt 1 ]; then
  echo "ERRO: backup nao contem nenhum CREATE TABLE. Deploy abortado, PROD INTACTO."
  rm -f "$DB_BACKUP"
  exit 2
fi

[ -f "$APP_JAR" ] && cp "$APP_JAR" "$RELEASES/_previous.jar"
cp "$INCOMING_JAR" "$RELEASES/app_${VERSION}.jar"
echo "    backup OK — $(numfmt --to=iec "$BACKUP_BYTES" 2>/dev/null || echo "${BACKUP_BYTES}B"), ${TABELAS} tabelas, flyway rank ${FLYWAY_ANTES}"

echo "==> [2/4] Subindo versao $VERSION"
sudo systemctl stop "$SERVICE" || true
sudo cp "$RELEASES/app_${VERSION}.jar" "$APP_JAR"
sudo chown "$APP_USER:$APP_USER" "$APP_JAR"
sudo systemctl start "$SERVICE"

echo "==> [3/4] Health-gate (ate ${HEALTH_TIMEOUT}s)"
ok=0
for i in $(seq 1 "$HEALTH_TIMEOUT"); do
  if curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then ok=1; break; fi
  sleep 1
done

if [ "$ok" = "1" ]; then
  echo "==> [4/4] OK — versao $VERSION no ar."
  echo "$VERSION" | sudo tee /app/CURRENT_VERSION >/dev/null

  # Cleanup TOLERANTE: daqui pra frente nada pode derrubar o script.
  # Sem isso, um grep sem match no primeiro deploy mata o script DEPOIS de um
  # deploy bem-sucedido e o CI marca falso positivo de falha.
  set +e
  set +o pipefail
  ls -1t "$RELEASES"/app_*.jar      2>/dev/null | tail -n +$((KEEP+1)) | xargs -r rm -f
  ls -1t "$RELEASES"/db_before_*.gz 2>/dev/null | tail -n +$((KEEP+1)) | xargs -r rm -f
  exit 0
fi

echo "==> [4/4] FALHA — ROLLBACK automatico do JAR"
echo "    Ultimos 50 logs da versao que falhou:"
sudo journalctl -u "$SERVICE" -n 50 --no-pager 2>/dev/null | sed 's/^/      /'

sudo systemctl stop "$SERVICE" || true
if [ -n "$PREV" ] && [ -f "$RELEASES/app_${PREV}.jar" ]; then
  sudo cp "$RELEASES/app_${PREV}.jar" "$APP_JAR"
else
  sudo cp "$RELEASES/_previous.jar" "$APP_JAR"
fi
sudo chown "$APP_USER:$APP_USER" "$APP_JAR"
sudo systemctl start "$SERVICE"
sleep 5

if curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
  echo "    ROLLBACK OK — versao estavel ($PREV) restaurada e no ar."
  analisar_banco "$FLYWAY_ANTES"
  exit 1
else
  echo "    CATASTROFE: rollback feito mas health nao respondeu."
  echo "    Ver: sudo journalctl -u $SERVICE -n 50"
  analisar_banco "$FLYWAY_ANTES"
  exit 3
fi
