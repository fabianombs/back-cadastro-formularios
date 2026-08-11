#!/usr/bin/env bash
# =============================================================================
# checar-74.sh                                                (FABIANO-74)
#
# SOMENTE LEITURA. Nenhum INSERT, nenhum restart, nenhum container recriado.
#
# Responde tres perguntas que o card deixou em aberto:
#
#   1. HOJE, o pool esta no banco certo? (a conferencia que o FABIANO-48 pedia)
#   2. Qual e o cache de DNS efetivo desta JVM?
#   3. Por quanto tempo uma conexao nascida errada continuaria errada?
#
# POR QUE NAO BASTA UM 'SELECT @@read_only' PELO CLIENTE mysql
#
# Foi exatamente isso que deu falso positivo em 08/08. O cliente de linha de
# comando e um processo NOVO: resolve o DNS na hora e acerta o banco novo. A
# aplicacao e um processo VELHO, com conexoes TCP ja estabelecidas. Os dois
# podem estar falando com servidores diferentes e ambos responderem "tudo bem".
#
# A unica prova que vale e comparar o IP das conexoes ABERTAS do container com
# o IP que o endpoint resolve agora. E isso nao precisa de escrita nenhuma.
# =============================================================================
set -uo pipefail

DIR_DEPLOY="${DIR_DEPLOY:-/home/ec2-user/fabiano/deploy}"
CONTAINER="fabiano-backend"

cd "$DIR_DEPLOY" || { echo "ERRO: $DIR_DEPLOY nao existe"; exit 1; }
env_get() { grep -E "^${1}=" .env 2>/dev/null | head -1 | cut -d= -f2-; }

DB_HOST=$(env_get DB_HOST)
[ -n "$DB_HOST" ] || { echo "ERRO: DB_HOST ausente no .env"; exit 1; }
if echo "$DB_HOST" | grep -q homolog; then AMBIENTE="HOMOLOGACAO"; else AMBIENTE="PRODUCAO"; fi

echo "============================================================"
echo " FABIANO-74 — o pool esta no banco certo?  ($AMBIENTE)"
echo " maquina: $(hostname)   $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M:%S')"
echo "============================================================"

# --- 1. onde o endpoint aponta AGORA -----------------------------------------
IP_ESPERADO=$(getent hosts "$DB_HOST" | awk '{print $1; exit}')
[ -n "$IP_ESPERADO" ] || { echo "ABORTADO: nao consegui resolver $DB_HOST"; exit 1; }
echo
echo "endpoint do .env: $DB_HOST"
echo "resolve para:     $IP_ESPERADO"

# /proc/net/tcp guarda o endereco em hexadecimal com os bytes INVERTIDOS
# (little-endian). 172.31.14.180 vira B40E1FAC, e nao AC1F0EB4.
hexinv() {
  local o1 o2 o3 o4; IFS=. read -r o1 o2 o3 o4 <<< "$1"
  printf '%02X%02X%02X%02X' "$o4" "$o3" "$o2" "$o1"
}
HEX_ESPERADO=$(hexinv "$IP_ESPERADO")
echo "em hex invertido: $HEX_ESPERADO   (porta 3306 = 0CEA)"

# --- 2. com quem o container esta realmente falando --------------------------
# 'ss' no host nao serve: as conexoes vivem no namespace de rede do container.
# /proc/net/tcp lido de DENTRO do container e a fonte certa.
TCP=$(docker exec "$CONTAINER" cat /proc/net/tcp 2>/dev/null)
if [ -z "$TCP" ]; then
  echo "ABORTADO: nao consegui ler /proc/net/tcp de dentro do $CONTAINER."
  exit 1
fi

echo
echo "--- conexoes do container na porta 3306, por destino ---"
# Desmonta o hex de volta para IP para que a saida seja legivel sem calculadora.
# A conversao vai no shell, e nao em awk: 'strtonum' so existe no gawk, e o awk
# desta maquina pode ser o mawk/busybox — falharia com "function never defined".
LISTA=$(echo "$TCP" | awk 'NR>1 { split($3,d,":"); if (d[2]=="0CEA") print d[1] }' | sort | uniq -c)
if [ -z "$LISTA" ]; then
  echo "    NENHUMA conexao na porta 3306"
else
  echo "$LISTA" | while read -r n h; do
    a=$((16#${h:6:2})); b=$((16#${h:4:2})); c=$((16#${h:2:2})); e=$((16#${h:0:2}))
    marca=""
    [ "$h" = "$HEX_ESPERADO" ] && marca="   <<< o endpoint do .env"
    printf '    %-16s %s  -> %s conexao(oes)%s\n' "${a}.${b}.${c}.${e}" "$h" "$n" "$marca"
  done
fi

NO_CERTO=$(echo "$TCP" | grep -c "${HEX_ESPERADO}:0CEA")
TOTAL_3306=$(echo "$TCP" | awk 'NR>1 { split($3,d,":"); if (d[2]=="0CEA") n++ } END { print n+0 }')

echo
echo "    no endpoint do .env ...... ${NO_CERTO}"
echo "    total na porta 3306 ...... ${TOTAL_3306}"

# --- 3. o cache de DNS desta JVM ---------------------------------------------
echo
echo "--- cache de DNS da JVM ---"
# Se a linha estiver comentada no java.security e nao houver -D no JAVA_OPTS,
# vale o default da JVM sem SecurityManager: 30 segundos de cache positivo.
CONF=$(docker exec "$CONTAINER" sh -c '
  for f in "$JAVA_HOME"/conf/security/java.security /opt/java/openjdk/conf/security/java.security \
           /usr/lib/jvm/*/conf/security/java.security; do
    [ -f "$f" ] && { grep -E "^[[:space:]]*#?[[:space:]]*networkaddress\.cache\.(negative\.)?ttl" "$f"; break; }
  done' 2>/dev/null)
echo "  java.security:"
printf '%s\n' "${CONF:-    (nao encontrei o arquivo)}" | sed 's/^/    /'

echo "  JAVA_OPTS do processo:"
docker exec "$CONTAINER" sh -c 'echo "$JAVA_OPTS"' 2>/dev/null | sed 's/^/    /'

if docker exec "$CONTAINER" sh -c 'echo "$JAVA_OPTS"' 2>/dev/null | grep -q 'networkaddress.cache.ttl'; then
  echo "  => ha -Dnetworkaddress.cache.ttl definido"
else
  echo "  => NAO ha -Dnetworkaddress.cache.ttl: vale o default (30s sem SecurityManager)"
fi

# --- 4. por quanto tempo uma conexao errada continuaria errada ---------------
echo
echo "--- tempo de vida das conexoes do pool ---"
ML=$(grep -rhE '^spring\.datasource\.hikari\.max-lifetime' /dev/null 2>/dev/null)
echo "  max-lifetime configurado no projeto: ${ML:-<nao configurado>}"
echo "  => sem configuracao, o default do HikariCP e 1800000 ms = 30 minutos."
echo
echo "  E ESTE o numero que explica os 20 minutos do incidente, e nao o DNS:"
echo "  o cache de DNS decide como a conexao NASCE errada (janela de segundos);"
echo "  o max-lifetime decide por quanto tempo ela CONTINUA errada."

# --- veredito ----------------------------------------------------------------
echo
echo "============================================================"
if [ "$TOTAL_3306" = "0" ]; then
  echo " INCONCLUSIVO: o container nao tem conexao aberta na 3306 agora."
  echo " O pool pode estar ocioso. Rode de novo depois de usar o sistema."
elif [ "$NO_CERTO" = "$TOTAL_3306" ]; then
  echo " OK — as ${TOTAL_3306} conexoes estao no endpoint do .env."
  echo " Nenhuma sobrou apontando para um banco antigo."
else
  echo " >>> ATENCAO: ${NO_CERTO} de ${TOTAL_3306} conexoes no endpoint certo."
  echo " As demais estao em OUTRO servidor. Ver a lista por destino acima."
  echo " Correcao: docker compose up -d --pull never --force-recreate backend"
  echo "           sleep 25 && docker exec fabiano-nginx nginx -s reload"
fi
echo "============================================================"
echo " Nada foi alterado. Somente leitura."
