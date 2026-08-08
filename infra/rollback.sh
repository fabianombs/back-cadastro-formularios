#!/usr/bin/env bash
# rollback.sh <versao> [--with-db]
set -uo pipefail
VERSION="${1:?Use: rollback.sh <versao> [--with-db]}"
WITH_DB="${2:-}"

SERVICE="poc-fabiano"; APP_JAR="/app/app.jar"; APP_USER="appuser"
RELEASES="/app/releases"; HEALTH_URL="http://localhost:8080/actuator/health"

JAR="$RELEASES/app_${VERSION}.jar"
if [ ! -f "$JAR" ]; then
  echo "Versao $VERSION nao encontrada. Disponiveis:"
  ls -1 "$RELEASES"/app_*.jar 2>/dev/null | sed -E 's#.*/app_(.*)\.jar#  - \1#'
  exit 1
fi

echo "==> Rollback do APP para $VERSION"
sudo systemctl stop "$SERVICE" || true
sudo cp "$JAR" "$APP_JAR"
sudo chown "$APP_USER:$APP_USER" "$APP_JAR"

if [ "$WITH_DB" = "--with-db" ]; then
  DUMP="$RELEASES/db_before_${VERSION}.sql.gz"
  [ -f "$DUMP" ] || { echo "Dump da $VERSION nao encontrado: $DUMP"; exit 1; }
  echo "!! ATENCAO: restaurando o banco de $VERSION — dados criados depois serao PERDIDOS."
  PID=$(systemctl show -p MainPID "$SERVICE" 2>/dev/null | cut -d= -f2)
  get() {
    local key="$1" v=""
    if [ -n "$PID" ] && [ "$PID" != "0" ] && [ -e "/proc/$PID/environ" ]; then
      v=$(sudo cat "/proc/$PID/environ" 2>/dev/null | tr '\0' '\n' | grep "^$key=" | head -1 | cut -d= -f2-)
    fi
    [ -z "$v" ] && v=$(systemctl show -p Environment "$SERVICE" 2>/dev/null | sed -n 's/^Environment=//p' | tr ' ' '\n' | grep "^$key=" | head -1 | cut -d= -f2-)
    echo "$v"
  }
  DB_HOST=$(get DB_HOST); DB_PORT=$(get DB_PORT); [ -z "$DB_PORT" ] && DB_PORT=3306
  DB_NAME=$(get DB_NAME); DB_USER=$(get DB_USER); DB_PASSWORD=$(get DB_PASSWORD)
  mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" --single-transaction --set-gtid-purged=OFF "$DB_NAME" \
    | gzip > "$RELEASES/db_safety_$(date +%Y%m%d_%H%M%S).sql.gz"
  gunzip -c "$DUMP" | mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME"
fi

sudo systemctl start "$SERVICE"
echo "$VERSION" | sudo tee /app/CURRENT_VERSION >/dev/null
sleep 5
curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"' \
  && echo "Rollback OK -> versao $VERSION no ar." \
  || { echo "ATENCAO: nao subiu. Ver: sudo journalctl -u $SERVICE -n 50"; exit 1; }
