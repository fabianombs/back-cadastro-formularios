#!/usr/bin/env bash
#
# deploy-safe.sh <jar-novo> <versao>
#   Ex: sudo ./deploy-safe.sh /home/ec2-user/app.jar 1.0.1
#
# Deploy seguro: se o backup nao for possivel, ABORTA antes de tocar no servico
# (prod fica intacto). Se a nova versao nao subir, faz ROLLBACK automatico.
set -uo pipefail

INCOMING_JAR="${1:?Use: deploy-safe.sh <jar-novo> <versao>}"
VERSION="${2:?informe a versao, ex: 1.0.1}"

SERVICE="poc-fabiano"
APP_JAR="/app/app.jar"
APP_USER="appuser"
RELEASES="/app/releases"
HEALTH_URL="http://localhost:8080/actuator/health"
HEALTH_TIMEOUT=90
KEEP=8

mkdir -p "$RELEASES"

# Credenciais do banco a partir do ambiente do servico systemd
genv() { systemctl show "$SERVICE" -p Environment --value | tr ' ' '\n' | grep "^$1=" | head -1 | cut -d= -f2-; }
DB_URL=$(genv DB_URL); DB_USER=$(genv DB_USER); DB_PASSWORD=$(genv DB_PASSWORD)

# GUARDA 1: sem credenciais nao da pra fazer backup -> aborta SEM mexer no servico
if [ -z "$DB_URL" ] || [ -z "$DB_USER" ]; then
  echo "ERRO: nao consegui ler DB_URL/DB_USER do servico $SERVICE. Deploy abortado, PROD INTACTO."
  echo "      (ajuste como o deploy-safe.sh le as credenciais antes de usar no CI)"
  exit 1
fi
DB_HOST=$(echo "$DB_URL" | sed -E 's#jdbc:mysql://([^:/]+).*#\1#')
DB_PORT=$(echo "$DB_URL" | sed -E 's#jdbc:mysql://[^:/]+:([0-9]+)/.*#\1#'); [ "$DB_PORT" = "$DB_URL" ] && DB_PORT=3306
DB_NAME=$(echo "$DB_URL" | sed -E 's#.*/([^?]+).*#\1#')

PREV=$(cat /app/CURRENT_VERSION 2>/dev/null || echo "")

echo "==> [1/4] Backup (dump pre-migration + release $VERSION)"
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" \
  --single-transaction --routines --triggers "$DB_NAME" 2>/tmp/dump.err | gzip > "$RELEASES/db_before_${VERSION}.sql.gz"

# GUARDA 2: backup vazio/falho -> aborta SEM mexer no servico
if [ ! -s "$RELEASES/db_before_${VERSION}.sql.gz" ]; then
  echo "ERRO: backup do banco falhou (arquivo vazio). Deploy abortado, PROD INTACTO."
  cat /tmp/dump.err 2>/dev/null | tail -5
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
