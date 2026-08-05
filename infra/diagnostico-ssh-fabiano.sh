#!/usr/bin/env bash
# =============================================================================
# Diagnostico via SSH na EC2 — Projeto Fabiano
# =============================================================================
# Rodar DENTRO da EC2, depois de:
#   ssh -i ~/.ssh/poc-fabiano ec2-user@100.30.35.83
#
# SOMENTE LEITURA, exceto a ETAPA 6 (backup), que so CRIA um arquivo novo.
# Nada e alterado no banco nem na aplicacao.
#
# Uso, ja dentro da EC2:
#   bash diagnostico-ssh.sh 2>&1 | tee ~/diagnostico-ssh.txt
# =============================================================================

set -uo pipefail

echo "############################################################"
echo "# DIAGNOSTICO VIA SSH — $(hostname)"
echo "# Data: $(date '+%Y-%m-%d %H:%M:%S')"
echo "############################################################"

# -----------------------------------------------------------------------------
echo
echo "=== [1] EXISTE IAM ROLE NA INSTANCIA? ==="
# -----------------------------------------------------------------------------
# Se a EC2 tiver um instance profile, o AWS CLI funciona AQUI DENTRO sem
# nenhuma credencial configurada — ele pega token do metadata service sozinho.
# Seria o caminho mais limpo para os comandos de RDS.
TOKEN=$(curl -sS -X PUT "http://169.254.169.254/latest/api/token" \
        -H "X-aws-ec2-metadata-token-ttl-seconds: 300" 2>/dev/null)
ROLE=$(curl -sS -H "X-aws-ec2-metadata-token: $TOKEN" \
       http://169.254.169.254/latest/meta-data/iam/security-credentials/ 2>/dev/null)

if [ -n "$ROLE" ] && [[ "$ROLE" != *"404"* ]] && [[ "$ROLE" != *"Not Found"* ]]; then
  echo "SIM — instance profile encontrado: $ROLE"
  echo ">>> Se o AWS CLI estiver instalado aqui, ele ja funciona sem configurar nada."
else
  echo "NAO — a instancia nao tem IAM role anexada."
  echo ">>> Comandos de RDS/EC2 vao precisar de credencial vinda de fora."
fi

echo
echo "--- AWS CLI instalado na EC2? ---"
command -v aws >/dev/null 2>&1 && aws --version 2>&1 || echo "aws CLI nao instalado aqui"

echo
echo "--- O CLI consegue se autenticar daqui? ---"
aws sts get-caller-identity --output table 2>&1 | head -20

# -----------------------------------------------------------------------------
echo
echo "=== [2] SAUDE DA APLICACAO E DA MAQUINA ==="
# -----------------------------------------------------------------------------
echo "--- Servico ---"
sudo systemctl is-active poc-fabiano 2>&1
sudo systemctl status poc-fabiano --no-pager 2>&1 | head -12

echo
echo "--- Memoria (a t2.micro tem 1 GB; suspeita de OOM killer) ---"
free -h

echo
echo "--- Houve OOM killer matando o servico? ---"
# A nota do incidente de julho registra status=9/KILL sem 'Stopping...' antes,
# assinatura classica de OOM. Se confirmar, o t3.small deixa de ser requisito
# do Grafana e vira correcao de um bug que ja acontece hoje.
sudo dmesg 2>/dev/null | grep -i -E "out of memory|killed process|oom" | tail -20 \
  || echo "(nada no dmesg — pode ter rotacionado; ver journalctl abaixo)"

echo
echo "--- Reinicios/mortes do servico nos ultimos 30 dias ---"
sudo journalctl -u poc-fabiano --since "30 days ago" --no-pager 2>/dev/null \
  | grep -i -E "Stopped|Killed|status=9|status=143|Failed|Started" | tail -25

echo
echo "--- Disco ---"
df -h /

echo
echo "--- Certificado SSL (validade) ---"
sudo certbot certificates 2>&1 | grep -E "Certificate Name|Expiry Date|Domains" || echo "(certbot nao encontrado)"

echo
echo "--- Renovacao automatica configurada? ---"
sudo crontab -l 2>/dev/null | grep -i certbot || echo "(nada no crontab do root)"
sudo systemctl list-timers 2>/dev/null | grep -i certbot || echo "(sem certbot.timer)"

# -----------------------------------------------------------------------------
echo
echo "=== [3] CREDENCIAIS DE BANCO (nomes das variaveis, sem valores) ==="
# -----------------------------------------------------------------------------
# NAO imprime senha. So confirma quais variaveis existem no env da aplicacao.
sudo grep -oE "^[A-Z_]+=" /etc/poc-fabiano.env 2>/dev/null | tr -d '=' \
  || echo "(nao consegui ler /etc/poc-fabiano.env)"

# Carrega as credenciais so para as consultas abaixo
DB_HOST=$(sudo grep '^DB_HOST=' /etc/poc-fabiano.env | cut -d= -f2-)
DB_NAME=$(sudo grep '^DB_NAME=' /etc/poc-fabiano.env | cut -d= -f2-)
DB_USER=$(sudo grep '^DB_USER=' /etc/poc-fabiano.env | cut -d= -f2-)
DB_PASS=$(sudo grep '^DB_PASSWORD=' /etc/poc-fabiano.env | cut -d= -f2-)

if [ -z "${DB_HOST:-}" ]; then
  echo "Sem credenciais de banco — os blocos [4] a [6] nao vao rodar."
  exit 0
fi

MYSQL="mysql -h $DB_HOST -u $DB_USER -p$DB_PASS -N -B"

# -----------------------------------------------------------------------------
echo
echo "=== [4] BANCO: VERSAO, TAMANHO E CHARSET ==="
# -----------------------------------------------------------------------------
echo "--- Versao atual ---"
$MYSQL -e "SELECT VERSION();" 2>&1

echo
echo "--- Tamanho total do banco ---"
$MYSQL -e "
SELECT table_schema AS banco,
       ROUND(SUM(data_length+index_length)/1024/1024, 1) AS tamanho_mb,
       COUNT(*) AS tabelas
FROM information_schema.tables
WHERE table_schema = '$DB_NAME'
GROUP BY table_schema;" 2>&1

echo
echo "--- 10 maiores tabelas ---"
$MYSQL -e "
SELECT table_name,
       table_rows AS linhas_aprox,
       ROUND((data_length+index_length)/1024/1024, 1) AS mb
FROM information_schema.tables
WHERE table_schema = '$DB_NAME'
ORDER BY (data_length+index_length) DESC LIMIT 10;" 2>&1

echo
echo "--- Engines e charsets fora do padrao (esperado: InnoDB + utf8mb4) ---"
$MYSQL -e "
SELECT engine, table_collation, COUNT(*) AS qtd
FROM information_schema.tables
WHERE table_schema = '$DB_NAME'
GROUP BY engine, table_collation;" 2>&1

# -----------------------------------------------------------------------------
echo
echo "=== [5] PRECHECK MANUAL DE COMPATIBILIDADE COM 8.4 ==="
# -----------------------------------------------------------------------------
echo "--- 5a. Plugin de autenticacao dos usuarios ---"
# No 8.4 o mysql_native_password deixa de vir habilitado por padrao.
# Se o usuario da aplicacao estiver nele, a app para de conectar apos o upgrade.
$MYSQL -e "SELECT user, host, plugin FROM mysql.user;" 2>&1

echo
echo "--- 5b. Chaves estrangeiras sobre indice nao-unico ou parcial ---"
# O 8.4 rejeita isso por padrao (restrict_fk_on_non_standard_key).
# Este projeto tem migrations mexendo em FK (V26 e V27), entao vale conferir.
$MYSQL -e "
SELECT rc.constraint_name, rc.table_name, rc.referenced_table_name, rc.unique_constraint_name
FROM information_schema.referential_constraints rc
WHERE rc.constraint_schema = '$DB_NAME';" 2>&1

echo
echo "--- 5c. AUTO_INCREMENT em coluna FLOAT/DOUBLE (removido no 8.4) ---"
$MYSQL -e "
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = '$DB_NAME'
  AND extra LIKE '%auto_increment%'
  AND data_type IN ('float','double');" 2>&1
echo "(vazio acima = OK)"

echo
echo "--- 5d. Tabelas criadas por usuario dentro do schema sys (bloqueia upgrade) ---"
$MYSQL -e "
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'sys' AND table_type = 'BASE TABLE';" 2>&1
echo "(vazio acima = OK)"

echo
echo "--- 5e. Estado das migrations do Flyway ---"
$MYSQL "$DB_NAME" -e "
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC LIMIT 10;" 2>&1

echo
echo "--- 5f. Alguma migration falhou em algum momento? ---"
$MYSQL "$DB_NAME" -e "
SELECT version, description, installed_on
FROM flyway_schema_history WHERE success = 0;" 2>&1
echo "(vazio acima = OK)"

# -----------------------------------------------------------------------------
echo
echo "=== [6] BACKUP COMPLETO AGORA (a parte que mais importa) ==="
# -----------------------------------------------------------------------------
# Nao depende de credencial AWS nenhuma. Roda direto contra o RDS pelo cliente
# mysql. E a forma de ter uma copia integra HOJE, sem esperar acesso do Fabiano.
#
# --single-transaction: dump consistente sem travar as tabelas (InnoDB)
# --routines --triggers --events: leva tambem procedures, triggers e eventos
BACKUP_DIR="$HOME/backups"
mkdir -p "$BACKUP_DIR"
ARQ="$BACKUP_DIR/fabiano-$(date +%Y%m%d-%H%M%S).sql.gz"

echo "Gerando dump em $ARQ ..."
mysqldump -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASS" \
  --single-transaction --routines --triggers --events \
  --set-gtid-purged=OFF \
  "$DB_NAME" 2>/tmp/dump.err | gzip -9 > "$ARQ"

if [ -s "$ARQ" ]; then
  echo "OK — backup gerado:"
  ls -lh "$ARQ"
  echo
  echo "Conferencia de integridade (deve terminar com 'Dump completed'):"
  gunzip -c "$ARQ" | tail -3
else
  echo "FALHOU — o dump saiu vazio. Erro:"
  cat /tmp/dump.err | tail -10
fi

echo
echo "############################################################"
echo "# FIM"
echo "#"
echo "# IMPORTANTE: o backup acima esta NA PROPRIA EC2. Se a instancia"
echo "# morrer, ele morre junto. Traga uma copia para a sua maquina:"
echo "#"
echo "#   scp -i ~/.ssh/poc-fabiano \\"
echo "#     ec2-user@100.30.35.83:~/backups/fabiano-*.sql.gz ."
echo "#"
echo "############################################################"
