#!/usr/bin/env bash
# deploy-safe.sh <jar-novo> <versao>
# Deploy seguro: falha-segura (aborta antes de tocar no servico se o backup nao for possivel)
# + health-gate + rollback automatico. Compatível com systemd antigo (sem --value).
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

mkdir -p "$RELEASES"

# --- Credenciais do banco do ambiente REAL do processo (cobre Environment= e EnvironmentFile=) ---
# Prod usa DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD. Sem 'systemctl --value' (systemd antigo).
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
  exit 1
fi

PREV=$(cat /app/CURRENT_VERSION 2>/dev/null || echo "")

echo "==> [1/4] Backup (dump pre-migration + release $VERSION)"
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" \
  --single-transaction --routines --triggers "$DB_NAME" 2>/tmp/dump.err | gzip > "$RELEASES/db_before_${VERSION}.sql.gz"

# GUARDA 2: backup vazio/falho -> aborta SEM mexer no servico
if [ ! -s "$RELEASES/db_before_${VERSION}.sql.gz" ]; then
  echo "ERRO: backup do banco falhou. Deploy abortado, PROD INTACTO."
  tail -5 /tmp/dump.err 2>/dev/null
  rm -f "$RELEASES/db_before_${VERSION}.sql.gz"
  exit 1
fi
[ -f "$APP_JAR" ] && cp "$APP_JAR" "$RELEASES/_previous.jar"
cp "$INCOMING_JAR" "$RELEASES/app_${VERSION}.jar"
echo "    backup OK"

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
  ls -1t "$RELEASES"/app_*.jar      2>/dev/null | tail -n +$((KEEP+1)) | xargs -r rm -f
  ls -1t "$RELEASES"/db_before_*.gz 2>/dev/null | tail -n +$((KEEP+1)) | xargs -r rm -f
  exit 0
fi

echo "==> [4/4] FALHA — ROLLBACK automatico"
sudo systemctl stop "$SERVICE" || true
if [ -n "$PREV" ] && [ -f "$RELEASES/app_${PREV}.jar" ]; then
  sudo cp "$RELEASES/app_${PREV}.jar" "$APP_JAR"
else
  sudo cp "$RELEASES/_previous.jar" "$APP_JAR"
fi
sudo chown "$APP_USER:$APP_USER" "$APP_JAR"
gunzip -c "$RELEASES/db_before_${VERSION}.sql.gz" | mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME"
sudo systemctl start "$SERVICE"
sleep 5
curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"' \
  && echo "    ROLLBACK OK — versao estavel ($PREV) restaurada." \
  || echo "    ATENCAO: rollback feito mas health nao respondeu. Ver: sudo journalctl -u $SERVICE -n 50"
exit 1
