#!/usr/bin/env bash
# =============================================================================
# checar-31-32.sh                              (FABIANO-31 e FABIANO-32)
#
# Duas verificacoes que so podem ser feitas numa maquina com o banco e a imagem:
#
#   FABIANO-31  o tipo REAL da coluna attendance_column_order no banco confere
#               com o que a migration V61 declara (TEXT NULL)?
#   FABIANO-32  a aplicacao RECUSA subir sem JWT_SECRET, e com erro claro?
#
# Roda so em homologacao. A checagem do 32 sobe um segundo container contra o
# mesmo banco; ele nao publica porta, nao recebe trafego e morre sozinho.
# =============================================================================
set -uo pipefail

DIR_DEPLOY="${DIR_DEPLOY:-/home/ec2-user/fabiano/deploy}"
cd "$DIR_DEPLOY" || { echo "ERRO: $DIR_DEPLOY nao existe"; exit 1; }

env_get() { grep -E "^${1}=" .env 2>/dev/null | head -1 | cut -d= -f2-; }

echo "maquina: $(hostname)   $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M:%S')"

if ! env_get DB_HOST | grep -q 'homolog'; then
  echo "ABORTADO: o .env desta maquina nao aponta para o banco de homologacao."
  exit 1
fi

DB_HOST=$(env_get DB_HOST); DB_NAME=$(env_get DB_NAME); DB_USER=$(env_get DB_USER)
TAG=$(env_get BACKEND_TAG); OWNER=$(env_get GHCR_OWNER)
export MYSQL_PWD="$(env_get DB_PASSWORD)"

RES31="?"; RES32="?"

# =============================================================================
echo
echo "=== FABIANO-31 — tipo real de form_templates.attendance_column_order ==="
# =============================================================================
LINHA=$(mysql -h "$DB_HOST" -u "$DB_USER" -N -B "$DB_NAME" -e "
  SELECT CONCAT(DATA_TYPE, ' | nullable=', IS_NULLABLE, ' | default=', IFNULL(COLUMN_DEFAULT,'NULL'))
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME   = 'form_templates'
    AND COLUMN_NAME  = 'attendance_column_order';" 2>/dev/null)

if [ -z "$LINHA" ]; then
  RES31="FALHOU — a coluna NAO EXISTE neste banco"
else
  echo "  $LINHA"
  # A V61 declara 'TEXT NULL'. Qualquer outra coisa significa que producao e o
  # que a migration cria divergem — um ambiente novo nasceria diferente.
  if echo "$LINHA" | grep -q '^text | nullable=YES'; then
    RES31="OK — text NULL, igual ao que a V61 cria"
  else
    RES31="DIVERGENTE — a V61 cria 'TEXT NULL' e o banco tem: $LINHA"
  fi
fi
echo "  $RES31"

# Confirma tambem que a V61 esta registrada no Flyway e passou.
echo
echo "  registro da V61 no flyway_schema_history:"
mysql -h "$DB_HOST" -u "$DB_USER" -N -B "$DB_NAME" -e "
  SELECT CONCAT('    rank ', installed_rank, ' | V', version, ' | ', description,
                ' | success=', success)
  FROM flyway_schema_history WHERE version IN ('61','62') ORDER BY installed_rank;" 2>/dev/null

# =============================================================================
echo
echo "=== FABIANO-32 — a aplicacao recusa subir sem JWT_SECRET? ==="
# =============================================================================
if [ -z "$TAG" ] || [ -z "$OWNER" ]; then
  RES32="FALHOU — BACKEND_TAG ou GHCR_OWNER ausente no .env"
else
  ENVSEM="$HOME/env-sem-jwt"
  rm -f "$ENVSEM"
  grep -v '^JWT_SECRET=' .env > "$ENVSEM"
  chmod 600 "$ENVSEM"   # contem a senha do banco

  if grep -q '^JWT_SECRET=' "$ENVSEM"; then
    echo "  ERRO: o filtro nao removeu a variavel. Abortando esta checagem."
    RES32="FALHOU — nao consegui montar o ambiente sem JWT_SECRET"
  else
    echo "  subindo a imagem :$TAG sem a variavel (ate 150s)..."
    LOG="$HOME/teste-sem-jwt.log"
    rm -f "$LOG"
    # --network none NAO da: o app precisa do RDS para o Flyway. Sem porta
    # publicada e sem nome de container fixo, este processo nao recebe trafego.
    timeout 150 docker run --rm --env-file "$ENVSEM" \
      "ghcr.io/${OWNER}/fabiano-back:${TAG}" > "$LOG" 2>&1
    RC=$?
    rm -f "$ENVSEM"

    echo
    echo "  --- ultimas 25 linhas ---"
    tail -25 "$LOG" | sed 's/^/    /'
    echo "  --- codigo de saida: $RC ---"

    if [ "$RC" = "124" ]; then
      RES32="FALHOU — a aplicacao SUBIU sem JWT_SECRET (timeout de 150s sem morrer)"
    elif grep -qi 'Could not resolve placeholder .jwt.secret\|placeholder .JWT_SECRET' "$LOG"; then
      RES32="OK — recusou subir, e a mensagem nomeia jwt.secret/JWT_SECRET"
    elif [ "$RC" != "0" ]; then
      RES32="PARCIAL — nao subiu (rc=$RC), mas a mensagem nao nomeia o JWT_SECRET"
    else
      RES32="FALHOU — saiu com 0, comportamento inesperado"
    fi
  fi
fi
echo "  $RES32"

echo
echo "============================================================"
echo " FABIANO-31  $RES31"
echo " FABIANO-32  $RES32"
echo "============================================================"
echo " backend em execucao: $(docker inspect --format='{{.State.Health.Status}}' fabiano-backend 2>/dev/null)"
