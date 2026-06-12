#!/usr/bin/env bash
#
# rollback.sh <versao> [--with-db]   -> volta o app para uma versao ja publicada.
#   sudo ./rollback.sh 1.0.0             # so o APP (seguro, mantem os dados)
#   sudo ./rollback.sh 1.0.0 --with-db   # tambem restaura o banco daquela epoca (PERDE dados novos)
set -uo pipefail

VERSION="${1:?Use: rollback.sh <versao> [--with-db]}"
WITH_DB="${2:-}"

SERVICE="poc-fabiano"
APP_JAR="/app/app.jar"
APP_USER="appuser"
RELEASES="/app/releases"
HEALTH_URL="http://localhost:8080/actuator/health"

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
  genv() { systemctl show "$SERVICE" -p Environment --value | tr ' ' '\n' | grep "^$1=" | head -1 | cut -d= -f2-; }
  DB_URL=$(genv DB_URL); DB_USER=$(genv DB_USER); DB_PASSWORD=$(genv DB_PASSWORD)
  DB_HOST=$(echo "$DB_URL" | sed -E 's#jdbc:mysql://([^:/]+).*#\1#')
  DB_PORT=$(echo "$DB_URL" | sed -E 's#jdbc:mysql://[^:/]+:([0-9]+)/.*#\1#'); [ "$DB_PORT" = "$DB_URL" ] && DB_PORT=3306
  DB_NAME=$(echo "$DB_URL" | sed -E 's#.*/([^?]+).*#\1#')
  mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" --single-transaction "$DB_NAME" \
    | gzip > "$RELEASES/db_safety_$(date +%Y%m%d_%H%M%S).sql.gz"
  gunzip -c "$DUMP" | mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME"
fi

sudo systemctl start "$SERVICE"
echo "$VERSION" | sudo tee /app/CURRENT_VERSION >/dev/null
sleep 5
curl -fsS "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"' \
  && echo "Rollback OK -> versao $VERSION no ar." \
  || { echo "ATENCAO: nao subiu. Ver: sudo journalctl -u $SERVICE -n 50"; exit 1; }
