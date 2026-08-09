#!/usr/bin/env bash
# =============================================================================
# Ensaio de restauracao de backup (FABIANO-20)
# =============================================================================
# Restaura um dump num MySQL 8.4 descartavel e compara a contagem de linhas
# com a producao. Responde tres perguntas que ninguem sabia responder:
#
#   1. o arquivo abre?
#   2. os dados estao la?
#   3. quanto tempo leva?  <- este numero e o RTO real
#
# Nao escreve nada na producao: so SELECT COUNT(*).
#
# Uso:
#   ./testar-restauracao.sh /app/backups/diario/fabiano-AAAAMMDD-HHMMSS.sql.gz
#
# Por que restaura num container e nao numa instancia RDS descartavel: o que
# esta sendo validado e o ARQUIVO, e para isso os dois sao equivalentes. Um
# container sobe em segundos e custa zero; um RDS levaria 15 min e entraria na
# fatura. A restauracao de snapshot do RDS e outro mecanismo, e esse a propria
# AWS exercita.
#
# Nota sobre credencial: a role da EC2 tem PutObject mas NAO GetObject no
# bucket de backup — backup append-only, decisao deliberada contra ransomware.
# Por isso este script trabalha com a copia local, depois de a copia do S3 ter
# sido provada identica por MD5 (ETag do objeto == md5sum do arquivo).
# =============================================================================
set -euo pipefail

DUMP="${1:?uso: $0 /caminho/do/dump.sql.gz}"
CONTAINER="restore-teste"
SENHA="descartavel-$$"
BANCO="poc_fabiano_new"
ENV_PROD="/home/ec2-user/fabiano/deploy/.env"

[ -f "$DUMP" ]     || { echo "ERRO: dump nao encontrado: $DUMP"; exit 1; }
[ -f "$ENV_PROD" ] || { echo "ERRO: .env nao encontrado: $ENV_PROD"; exit 1; }

# Mesmo arquivo que o backup-db.sh le, para o teste comparar contra o banco
# que o backup realmente copia — e nao contra um que alguem supos.
set -a
# shellcheck disable=SC1090
. "$ENV_PROD"
set +a

echo "== 1. subindo MySQL 8.4 descartavel =="
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$CONTAINER" \
  -e MYSQL_ROOT_PASSWORD="$SENHA" \
  -e MYSQL_DATABASE="$BANCO" \
  mysql:8.4 >/dev/null

# NAO usar 'mysqladmin ping' aqui. Durante a inicializacao a imagem oficial sobe
# um servidor TEMPORARIO para criar o banco e definir a senha do root, e o ping
# responde "vivo" contra ele — antes de a senha existir. O resultado e um
# 'Access denied' na etapa seguinte, quando tudo parecia pronto.
# Um SELECT autenticado so passa quando o root ja existe com a senha certa.
printf "   aguardando o banco aceitar conexao autenticada"
TENTATIVAS=0
until docker exec -e MYSQL_PWD="$SENHA" "$CONTAINER" mysql -uroot -e "SELECT 1" >/dev/null 2>&1; do
  printf "."
  sleep 2
  TENTATIVAS=$((TENTATIVAS + 1))
  if [ "$TENTATIVAS" -gt 60 ]; then
    echo " TIMEOUT"
    docker logs --tail 30 "$CONTAINER"
    exit 1
  fi
done
echo " pronto"

# O cronometro comeca AQUI, nao no docker run: numa restauracao de verdade o
# destino ja existe, entao o tempo de subir container nao faz parte do RTO.
echo "== 2. restaurando (cronometrado) =="
INICIO=$(date +%s)
sudo zcat "$DUMP" | docker exec -i -e MYSQL_PWD="$SENHA" "$CONTAINER" mysql -uroot "$BANCO"
FIM=$(date +%s)
RTO=$((FIM - INICIO))
echo "   restauracao levou ${RTO}s"

echo "== 3. comparando contagem de linhas com a producao =="
TABELAS=$(docker exec -e MYSQL_PWD="$SENHA" "$CONTAINER" mysql -uroot -N -B \
  -e "SELECT table_name FROM information_schema.tables
      WHERE table_schema='${BANCO}' ORDER BY table_name;")

TOTAL_TABELAS=0
DIVERGENCIAS=0
printf "%-44s %11s %11s  %s\n" "TABELA" "RESTAURADO" "PRODUCAO" ""
printf "%-44s %11s %11s  %s\n" "------" "----------" "--------" ""

while read -r t; do
  [ -z "$t" ] && continue
  TOTAL_TABELAS=$((TOTAL_TABELAS + 1))

  R=$(docker exec -e MYSQL_PWD="$SENHA" "$CONTAINER" mysql -uroot -N -B \
        -e "SELECT COUNT(*) FROM \`${BANCO}\`.\`${t}\`;" </dev/null)

  # MYSQL_PWD evita a senha aparecer no ps e no historico.
  P=$(MYSQL_PWD="$DB_PASSWORD" mysql -h "$DB_HOST" -P "${DB_PORT:-3306}" -u "$DB_USER" -N -B \
        -e "SELECT COUNT(*) FROM \`${DB_NAME}\`.\`${t}\`;" </dev/null 2>/dev/null || echo "?")

  if [ "$R" = "$P" ]; then
    MARCA="ok"
  else
    MARCA="<<< DIVERGE"
    DIVERGENCIAS=$((DIVERGENCIAS + 1))
  fi
  printf "%-44s %11s %11s  %s\n" "$t" "$R" "$P" "$MARCA"
done <<< "$TABELAS"

echo
echo "============================================================"
echo " dump testado    : $(basename "$DUMP")"
echo " tabelas         : ${TOTAL_TABELAS}"
echo " divergentes     : ${DIVERGENCIAS}"
echo " RTO medido      : ${RTO}s"
echo "============================================================"
echo
echo "Divergencia pequena e esperada: o dump e de um instante e a producao"
echo "continuou viva. Tabela FALTANDO, ou diferenca grande, e achado real."
echo
echo "Container '${CONTAINER}' segue de pe para inspecao manual:"
echo "   docker exec -it -e MYSQL_PWD='${SENHA}' ${CONTAINER} mysql -uroot ${BANCO}"
echo
echo "Apagar quando terminar:"
echo "   docker rm -f ${CONTAINER}"
