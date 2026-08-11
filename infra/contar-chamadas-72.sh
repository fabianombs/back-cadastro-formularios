#!/usr/bin/env bash
# =============================================================================
# contar-chamadas-72.sh                                       (FABIANO-72)
#
# SOMENTE LEITURA. So le o log do container.
#
# Conta quantas requisicoes o front dispara ao abrir UMA tela, agrupadas por
# rota, e imprime a linha do tempo para dar para ver a cascata.
#
# POR QUE O CARD NAO PODIA SABER OS PARAMETROS
#
# O AccessLogFilter registra 'requisicao.getRequestURI()', que NAO inclui a
# query string. No log, estas duas linhas sao identicas:
#
#   GET /attendance/template/39?page=0&size=1     (so para contar registros)
#   GET /attendance/template/39?page=0&size=500   (a planilha inteira)
#
# O card concluiu "duas chamadas com os mesmos parametros" a partir de quatro
# linhas de log. As chamadas existem; "mesmos parametros" nao era observavel.
#
# COMO USAR
#
#   bash contar-chamadas-72.sh 30
#
# Ele avisa, espera os segundos que voce pedir, e nesse intervalo voce abre a
# tela no navegador. Depois conta o que passou.
#
# Rode em HOMOLOGACAO se puder: em producao o Fabiano pode estar usando o
# sistema, e o trafego dele entra na contagem.
# =============================================================================
set -uo pipefail

JANELA="${1:-30}"
CONTAINER="fabiano-backend"
DIR_DEPLOY="${DIR_DEPLOY:-/home/ec2-user/fabiano/deploy}"

cd "$DIR_DEPLOY" || { echo "ERRO: $DIR_DEPLOY nao existe"; exit 1; }
env_get() { grep -E "^${1}=" .env 2>/dev/null | head -1 | cut -d= -f2-; }
if env_get DB_HOST | grep -q homolog; then AMBIENTE="HOMOLOGACAO"; else AMBIENTE="PRODUCAO"; fi

echo "============================================================"
echo " FABIANO-72 — quantas requisicoes uma tela dispara"
echo " ambiente: $AMBIENTE     maquina: $(hostname)"
echo "============================================================"

if [ "$AMBIENTE" = "PRODUCAO" ]; then
  echo
  echo " AVISO: isto e PRODUCAO. Se o Fabiano estiver usando o sistema agora,"
  echo "        as requisicoes dele entram na contagem e o numero perde o"
  echo "        sentido. Prefira homologacao."
  echo
fi

# Prova o acesso antes de contar: log vazio por falta de permissao seria lido
# como "o front nao chamou nada", que e o oposto da conclusao certa.
if ! docker logs --tail 1 "$CONTAINER" >/dev/null 2>&1; then
  echo "ABORTADO: nao consegui ler o log do $CONTAINER."
  exit 1
fi

echo
echo ">>> ABRA A TELA NO NAVEGADOR AGORA. Contando por ${JANELA}s..."
for i in $(seq "$JANELA" -1 1); do printf '\r    faltam %2ds ' "$i"; sleep 1; done
printf '\r    tempo esgotado.   \n'

LOG=$(docker logs --since "${JANELA}s" "$CONTAINER" 2>&1 \
        | grep -E '(GET|POST|PUT|PATCH|DELETE) /[^ ]* -> [0-9]+ em [0-9]+ ms')

if [ -z "$LOG" ]; then
  echo
  echo "Nenhuma requisicao no periodo. A tela abriu mesmo? O front aponta para"
  echo "esta maquina? (conferir a URL da API no build da Vercel)"
  exit 0
fi

echo
echo "=== POR ROTA (metodo + caminho, ordenado por quantidade) ==="
# O -o com a expressao ate o '->' descarta status e tempo: o que importa aqui e
# QUANTAS vezes a mesma rota foi chamada, nao quanto cada uma demorou.
echo "$LOG" \
  | grep -oE '(GET|POST|PUT|PATCH|DELETE) /[^ ]*' \
  | sort | uniq -c | sort -rn \
  | awk '{ printf "  %3d x  %s %s\n", $1, $2, $3 }'

echo
echo "=== LINHA DO TEMPO (para ver a cascata) ==="
# A hora fica no comeco da linha do logback. Cortar em 12 caracteres pega
# HH:MM:SS.mmm, que e a granularidade que separa chamadas de 200 ms.
echo "$LOG" | while IFS= read -r l; do
  hora=$(echo "$l" | grep -oE '[0-9]{2}:[0-9]{2}:[0-9]{2}[.,][0-9]{3}' | head -1)
  rota=$(echo "$l" | grep -oE '(GET|POST|PUT|PATCH|DELETE) /[^ ]* -> [0-9]+ em [0-9]+ ms')
  printf '  %s  %s\n' "${hora:-??:??:??.???}" "$rota"
done

TOTAL=$(echo "$LOG" | wc -l)
ROTAS=$(echo "$LOG" | grep -oE '(GET|POST|PUT|PATCH|DELETE) /[^ ]*' | sort -u | wc -l)
REPETIDAS=$(echo "$LOG" | grep -oE '(GET|POST|PUT|PATCH|DELETE) /[^ ]*' | sort | uniq -d | wc -l)

echo
echo "============================================================"
echo " requisicoes no periodo ....... ${TOTAL}"
echo " rotas distintas .............. ${ROTAS}"
echo " rotas chamadas MAIS DE UMA VEZ ${REPETIDAS}"
echo "============================================================"
echo
echo " LEMBRETE ao interpretar: o access log nao mostra a query string."
echo " Duas linhas iguais de /attendance/template/N podem ser page/size"
echo " diferentes — uma sondagem de size=1 e a planilha de size=500."
echo " A paginacao do getAllAttendance tambem gera uma requisicao por pagina:"
echo " 1005 registros com pageSize=500 sao TRES linhas, nao uma."
