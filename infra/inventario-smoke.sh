#!/usr/bin/env bash
# =============================================================================
# inventario-smoke.sh                                        (FABIANO-55)
#
# SOMENTE LEITURA. Nenhum DELETE, UPDATE ou INSERT.
#
# O smoke antigo criava um usuario novo ("smoke_" + numero aleatorio) a cada
# execucao e nunca removia. Este script mostra o que ficou para tras — e, mais
# importante, o que PENDURA nesses usuarios, porque apagar um usuario que e dono
# de formulario e de submissao nao e um DELETE, e uma decisao.
#
# Roda em qualquer das duas maquinas; le as credenciais do .env do compose.
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
echo " INVENTARIO DE CONTAS DE SMOKE — $AMBIENTE"
echo " maquina: $(hostname)   $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M')"
echo " banco:   $DB_NAME"
echo "============================================================"

q() { mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -t "$DB_NAME" -e "$1" 2>&1; }

echo
echo "--- users com username comecando em 'smoke' ---"
q "SELECT u.id, u.username, u.email,
          (SELECT COUNT(*) FROM clients c WHERE c.user_id = u.id) AS clientes
   FROM users u
   WHERE u.username LIKE 'smoke%'
   ORDER BY u.id;"

echo
echo "--- clients com username comecando em 'smoke' ---"
q "SELECT c.id, c.username, c.email, c.user_id, c.deleted
   FROM clients c
   WHERE c.username LIKE 'smoke%'
   ORDER BY c.id;"

echo
echo "--- o que PENDURA nesses clients ---"
# Um usuario de smoke que so ocupa uma linha e lixo. Um que e dono de template
# com submissao e presenca marcada e outra coisa: apagar leva dado junto, e o
# dado pode ter vindo de uma pessoa de verdade que respondeu um formulario de
# teste que foi divulgado por engano.
q "SELECT c.id AS cliente,
          c.username,
          (SELECT COUNT(*) FROM form_templates    t WHERE t.client_id = c.id) AS templates,
          (SELECT COUNT(*) FROM form_submissions  s
             JOIN form_templates t2 ON t2.id = s.form_template_id
            WHERE t2.client_id = c.id)                                        AS submissoes,
          (SELECT COUNT(*) FROM appointments      a
             JOIN form_templates t3 ON t3.id = a.form_template_id
            WHERE t3.client_id = c.id)                                        AS agendamentos
   FROM clients c
   WHERE c.username LIKE 'smoke%'
   ORDER BY c.id;"

echo
echo "--- o usuario de servico ATUAL (nao apagar) ---"
# O smoke de hoje usa SMOKE_USER, com 'smoke_servico' como padrao. Este e o
# unico que deve sobreviver.
q "SELECT id, username, email FROM users WHERE username = 'smoke_servico';"

echo
echo "--- totais ---"
q "SELECT
     (SELECT COUNT(*) FROM users   WHERE username LIKE 'smoke%')                        AS users_smoke,
     (SELECT COUNT(*) FROM users   WHERE username LIKE 'smoke%' AND username <> 'smoke_servico') AS users_para_avaliar,
     (SELECT COUNT(*) FROM clients WHERE username LIKE 'smoke%')                        AS clients_smoke;"

echo
echo "============================================================"
echo " Nada foi alterado. Somente SELECT."
echo "============================================================"
