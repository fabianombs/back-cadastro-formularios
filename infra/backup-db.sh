#!/usr/bin/env bash
# =============================================================================
# backup-db.sh — backup diario do banco de producao (FABIANO-20)
# =============================================================================
# Roda por cron na EC2, independente de deploy.
#
# O dump pre-deploy so existe quando alguem faz deploy. Se ninguem mexer no
# sistema por duas semanas, o backup mais recente tem duas semanas. Este script
# resolve isso.
#
# Rotacao avo-pai-filho (GFS):
#   diario   -> mantem 7
#   semanal  -> todo domingo, mantem 4
#   mensal   -> todo dia 1, mantem 12
#
# Instalacao:
#   sudo cp backup-db.sh /app/backup-db.sh && sudo chmod +x /app/backup-db.sh
#   (crontab abaixo, no fim do arquivo)
# =============================================================================

set -uo pipefail

BACKUP_ROOT="/app/backups"
LOG="/var/log/backup-db.log"
ENV_FILE="/etc/poc-fabiano.env"

# Um gzip VAZIO tem 20 bytes. Foi assim que os backups do deploy-safe.sh
# passaram meses parecendo validos. O minimo aqui e deliberadamente alto.
MIN_BACKUP_BYTES=10240

# Retencao por faixa
KEEP_DIARIO=7
KEEP_SEMANAL=4
KEEP_MENSAL=12

# Arquivo lido depois pelo Prometheus (node-exporter textfile collector).
# E o dead man's switch: o painel mostra "horas desde o ultimo backup OK" e
# alerta se passar de 26h. Backup que para de rodar nao gera erro nenhum —
# gera silencio, e silencio e o que ninguem percebe.
METRICA="/var/lib/node_exporter/textfile_collector/backup_fabiano.prom"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG"; }

falhar() {
  log "ERRO: $*"
  # Grava 0 na metrica de sucesso para o alerta disparar
  mkdir -p "$(dirname "$METRICA")" 2>/dev/null
  {
    echo "# HELP backup_fabiano_sucesso 1 se o ultimo backup terminou OK"
    echo "# TYPE backup_fabiano_sucesso gauge"
    echo "backup_fabiano_sucesso 0"
  } > "$METRICA" 2>/dev/null
  exit 1
}

mkdir -p "$BACKUP_ROOT"/{diario,semanal,mensal}

# --- Ferramentas presentes? --------------------------------------------------
command -v mysqldump >/dev/null 2>&1 || falhar "mysqldump nao instalado (sudo yum install -y mariadb)"

# --- Credenciais -------------------------------------------------------------
DB_HOST=$(sudo grep '^DB_HOST=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)
DB_PORT=$(sudo grep '^DB_PORT=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)
DB_NAME=$(sudo grep '^DB_NAME=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)
DB_USER=$(sudo grep '^DB_USER=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)
DB_PASS=$(sudo grep '^DB_PASSWORD=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)
[ -z "$DB_PORT" ] && DB_PORT=3306

[ -n "$DB_HOST" ] && [ -n "$DB_NAME" ] && [ -n "$DB_USER" ] \
  || falhar "nao consegui ler credenciais de $ENV_FILE"

# --- Dump --------------------------------------------------------------------
STAMP=$(date +%Y%m%d-%H%M%S)
ARQ="$BACKUP_ROOT/diario/fabiano-${STAMP}.sql.gz"

log "iniciando backup -> $ARQ"

# PIPESTATUS[0] pega o codigo do mysqldump, nao o do gzip.
# Sem isso um mysqldump que falha some atras de um gzip bem-sucedido.
mysqldump -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASS" \
  --single-transaction --routines --triggers "$DB_NAME" 2>/tmp/backup-dump.err \
  | gzip -9 > "$ARQ"
RC=${PIPESTATUS[0]}

# --- As cinco validacoes -----------------------------------------------------
[ "$RC" -eq 0 ] || { rm -f "$ARQ"; falhar "mysqldump retornou $RC — $(tail -3 /tmp/backup-dump.err | tr '\n' ' ')"; }

BYTES=$(stat -c%s "$ARQ" 2>/dev/null || echo 0)
[ "$BYTES" -ge "$MIN_BACKUP_BYTES" ] || { rm -f "$ARQ"; falhar "arquivo com apenas ${BYTES} bytes"; }

gunzip -c "$ARQ" 2>/dev/null | tail -5 | grep -q "Dump completed" \
  || { rm -f "$ARQ"; falhar "sem a marca 'Dump completed' — dump incompleto"; }

TABELAS=$(gunzip -c "$ARQ" 2>/dev/null | grep -c "CREATE TABLE" || echo 0)
[ "$TABELAS" -ge 1 ] || { rm -f "$ARQ"; falhar "nenhum CREATE TABLE no dump"; }

INSERTS=$(gunzip -c "$ARQ" 2>/dev/null | grep -c "^INSERT INTO" || echo 0)
# Dump so com estrutura tambem termina com "Dump completed" — por isso este teste
[ "$INSERTS" -ge 1 ] || { rm -f "$ARQ"; falhar "dump sem nenhum INSERT — veio so a estrutura"; }

log "backup OK — $(numfmt --to=iec "$BYTES" 2>/dev/null || echo "${BYTES}B"), ${TABELAS} tabelas, ${INSERTS} blocos de insert"

# --- Copias semanal e mensal (hardlink, nao ocupa espaco extra) --------------
DIA_SEMANA=$(date +%u)   # 7 = domingo
DIA_MES=$(date +%d)

if [ "$DIA_SEMANA" = "7" ]; then
  ln -f "$ARQ" "$BACKUP_ROOT/semanal/fabiano-${STAMP}.sql.gz" && log "copia semanal criada"
fi
if [ "$DIA_MES" = "01" ]; then
  ln -f "$ARQ" "$BACKUP_ROOT/mensal/fabiano-${STAMP}.sql.gz" && log "copia mensal criada"
fi

# --- Envio por e-mail para o cliente -----------------------------------------
# O Fabiano e o dono dos dados. Mandar a copia para ele nao e vazamento — e o
# contrario: ele deixa de depender exclusivamente do nosso servidor.
# Falha no envio NAO invalida o backup local, mas fica registrada no log.
ENVIAR="/app/enviar-backup-email.py"
if [ -x "$ENVIAR" ]; then
  if python3 "$ENVIAR" "$ARQ" "$TABELAS" "$INSERTS" >>"$LOG" 2>&1; then
    log "e-mail enviado ao cliente"
  else
    log "AVISO: falha ao enviar e-mail (o backup local esta OK — ver linhas acima)"
  fi
else
  log "AVISO: $ENVIAR nao encontrado — backup nao foi enviado por e-mail"
fi

# --- Copia offsite em S3 (quando houver credencial AWS) ----------------------
# ATENCAO: NAO usar o bucket de imagens da aplicacao — ele serve conteudo
# publico. Um dump com CPF ali dentro seria vazamento. Bucket proprio, privado.
BUCKET_BACKUP="${FABIANO_BACKUP_BUCKET:-}"
if [ -n "$BUCKET_BACKUP" ] && command -v aws >/dev/null 2>&1; then
  if aws s3 cp "$ARQ" "s3://${BUCKET_BACKUP}/diario/" --only-show-errors 2>>"$LOG"; then
    log "copia enviada para s3://${BUCKET_BACKUP}/diario/"
  else
    log "AVISO: falha ao enviar para o S3 (backup local esta OK)"
  fi
fi

# --- Rotacao GFS -------------------------------------------------------------
# Tolerante: nada aqui pode derrubar o script depois de um backup bem-sucedido.
set +e
set +o pipefail

ls -1t "$BACKUP_ROOT/diario"/*.sql.gz  2>/dev/null | tail -n +$((KEEP_DIARIO + 1))  | xargs -r rm -f
ls -1t "$BACKUP_ROOT/semanal"/*.sql.gz 2>/dev/null | tail -n +$((KEEP_SEMANAL + 1)) | xargs -r rm -f
ls -1t "$BACKUP_ROOT/mensal"/*.sql.gz  2>/dev/null | tail -n +$((KEEP_MENSAL + 1))  | xargs -r rm -f

log "rotacao: $(ls -1 "$BACKUP_ROOT/diario"/*.sql.gz 2>/dev/null | wc -l) diarios, $(ls -1 "$BACKUP_ROOT/semanal"/*.sql.gz 2>/dev/null | wc -l) semanais, $(ls -1 "$BACKUP_ROOT/mensal"/*.sql.gz 2>/dev/null | wc -l) mensais"

# --- Metrica de sucesso ------------------------------------------------------
mkdir -p "$(dirname "$METRICA")" 2>/dev/null
{
  echo "# HELP backup_fabiano_sucesso 1 se o ultimo backup terminou OK"
  echo "# TYPE backup_fabiano_sucesso gauge"
  echo "backup_fabiano_sucesso 1"
  echo "# HELP backup_fabiano_timestamp_seconds momento do ultimo backup OK"
  echo "# TYPE backup_fabiano_timestamp_seconds gauge"
  echo "backup_fabiano_timestamp_seconds $(date +%s)"
  echo "# HELP backup_fabiano_bytes tamanho do ultimo backup"
  echo "# TYPE backup_fabiano_bytes gauge"
  echo "backup_fabiano_bytes $BYTES"
} > "$METRICA" 2>/dev/null

log "concluido"
exit 0

# =============================================================================
# INSTALACAO
# =============================================================================
# 1) Scripts
#   sudo cp backup-db.sh          /app/backup-db.sh
#   sudo cp enviar-backup-email.py /app/enviar-backup-email.py
#   sudo chmod +x /app/backup-db.sh /app/enviar-backup-email.py
#   sudo touch /var/log/backup-db.log && sudo chown ec2-user /var/log/backup-db.log
#
# 2) Credenciais de e-mail (chmod 600 — contem senha)
#   sudo tee /etc/fabiano-backup.env > /dev/null <<'EOF'
#   SMTP_HOST=smtp.gmail.com
#   SMTP_PORT=587
#   SMTP_USER=resulta.tecnologies@gmail.com
#   SMTP_PASS=SUA_APP_PASSWORD_16_CHARS
#   MAIL_TO=email-do-fabiano@exemplo.com.br
#   MAIL_CC=vinicius.politta1@gmail.com
#   EOF
#   sudo chmod 600 /etc/fabiano-backup.env
#
#   A App Password se cria em https://myaccount.google.com/apppasswords
#   (exige verificacao em duas etapas ativa na conta). NAO usar a senha normal.
#
# 3) Cron — 03:30 de Brasilia = 06:30 UTC, fora do horario de uso
#   sudo crontab -e
#   30 6 * * * /app/backup-db.sh >> /var/log/backup-db.log 2>&1
#
# TESTE — rodar na mao antes de confiar no cron:
#   sudo /app/backup-db.sh
#   ls -lh /app/backups/diario/
#   tail -30 /var/log/backup-db.log
#
# Na primeira vez, mandar MAIL_TO para voce mesmo. So depois de ver o e-mail
# chegando com o anexo certo e que vale apontar para o Fabiano.
# =============================================================================
