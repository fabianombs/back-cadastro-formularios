#!/usr/bin/env bash
# =============================================================================
# checar-53.sh                                               (FABIANO-53)
#
# SOMENTE LEITURA. Nenhum DELETE, UPDATE ou INSERT.
#
# O teste novo prova que o INSERT passou a carregar a data. Falta a outra
# metade: os clientes criados PELA APLICACAO, depois da correcao, nascem com
# created_at? E quantas linhas historicas ficaram com NULL?
#
# Roda em qualquer das duas maquinas.
# =============================================================================
set -uo pipefail

DIR_DEPLOY="${DIR_DEPLOY:-/home/ec2-user/fabiano/deploy}"
cd "$DIR_DEPLOY" || { echo "ERRO: $DIR_DEPLOY nao existe"; exit 1; }
env_get() { grep -E "^${1}=" .env 2>/dev/null | head -1 | cut -d= -f2-; }

DB_HOST=$(env_get DB_HOST); DB_NAME=$(env_get DB_NAME); DB_USER=$(env_get DB_USER)
DB_PORT=$(env_get DB_PORT); [ -n "$DB_PORT" ] || DB_PORT=3306
export MYSQL_PWD="$(env_get DB_PASSWORD)"

if echo "$DB_HOST" | grep -q homolog; then AMBIENTE="HOMOLOGACAO"; else AMBIENTE="PRODUCAO"; fi

echo "============================================================"
echo " created_at dos clientes — $AMBIENTE"
echo " maquina: $(hostname)   $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M')"
echo "============================================================"

q() { mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -t "$DB_NAME" -e "$1" 2>&1; }

echo
echo "--- quantos clientes tem data, e quantos nao tem ---"
q "SELECT COUNT(*)                                        AS total,
          SUM(created_at IS NULL)                         AS sem_created_at,
          SUM(created_at IS NOT NULL)                     AS com_created_at,
          SUM(updated_at IS NULL)                         AS sem_updated_at
   FROM clients;"

echo
echo "--- os 10 clientes mais recentes (id maior = criado depois) ---"
# Se os IDs mais altos tem data e os mais baixos nao, a correcao pegou: a
# fronteira entre NULL e preenchido e o deploy que levou o @CreationTimestamp.
q "SELECT id, username, created_at, updated_at
   FROM clients
   ORDER BY id DESC
   LIMIT 10;"

echo
echo "--- onde fica a fronteira ---"
q "SELECT MAX(id) AS ultimo_id_SEM_data FROM clients WHERE created_at IS NULL;"
q "SELECT MIN(id) AS primeiro_id_COM_data, MIN(created_at) AS data_mais_antiga
   FROM clients WHERE created_at IS NOT NULL;"

echo
echo "============================================================"
echo " Nada foi alterado. Somente SELECT."
echo "============================================================"
