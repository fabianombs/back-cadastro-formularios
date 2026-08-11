#!/usr/bin/env bash
# =============================================================================
# testar-podar-imagens.sh                                     (FABIANO-81)
#
# Harness da podar_imagens(). NAO toca em docker de verdade: substitui o binario
# por uma funcao que devolve um estado montado e ANOTA todo 'rmi' pedido.
#
# Mexer no deploy-safe.sh e mexer no caminho critico do deploy. A funcao precisa
# provar, antes de entrar la, que ela nao remove: a :previous, a :latest, a tag
# do deploy corrente, nem a imagem que o container esta rodando.
# =============================================================================
set -uo pipefail

IMAGEM="ghcr.io/resulta/fabiano-back"
CONTAINER="fabiano-backend"
REMOVIDAS=/tmp/rmi.log
FALHAS=0

# --- docker de mentira -------------------------------------------------------
# TAGS      = saida de 'docker images', da mais nova para a mais velha
# IDS       = "tag:id" separados por espaco
# EM_USO    = id que o container esta rodando (vazio = nenhum container)
docker() {
  case "$1 ${2:-}" in
    "images $IMAGEM")
      printf '%s\n' $TAGS ;;
    "images --no-trunc")
      local alvo="${4##*:}" par
      for par in $IDS; do
        [ "${par%%:*}" = "$alvo" ] && { echo "${par##*:}"; return; }
      done ;;
    "inspect --format="*)
      [ -n "$EM_USO" ] && echo "$EM_USO" || return 1 ;;
    "rmi "*)
      echo "${2##*:}" >> "$REMOVIDAS" ;;
    "image prune")
      echo "prune" >> "$REMOVIDAS" ;;
  esac
  return 0
}
df() { printf 'x\n/ 20G 8G 13G 40%% /\n'; }
export -f docker df 2>/dev/null || true

# A funcao e EXTRAIDA do deploy-safe.sh, nao copiada para ca. Uma copia local
# testaria uma versao que nao e a que roda em producao — que e exatamente o tipo
# de teste que passa enquanto o sistema quebra.
ALVO="${ALVO:-$(dirname "$0")/../deploy/scripts/deploy-safe.sh}"
[ -f "$ALVO" ] || { echo "ERRO: nao achei $ALVO"; exit 2; }
eval "$(sed -n '/^podar_imagens() {/,/^}/p' "$ALVO")"
KEEP_IMAGENS=$(grep -E '^KEEP_IMAGENS=' "$ALVO" | head -1 | cut -d= -f2)
command -v podar_imagens >/dev/null || { echo "ERRO: podar_imagens nao foi extraida"; exit 2; }
echo "funcao extraida de: $ALVO   (KEEP_IMAGENS=$KEEP_IMAGENS)"

# --- motor do teste ----------------------------------------------------------
# Compara o conjunto removido com o esperado. Conjunto, nao lista: a ordem em
# que o docker devolve as tags nao e contrato.
caso() {
  local nome="$1" esperado="$2"
  : > "$REMOVIDAS"
  podar_imagens >/dev/null 2>&1
  local obtido
  obtido=$(grep -v '^prune$' "$REMOVIDAS" | sort | tr '\n' ' ' | sed 's/ *$//')
  esperado=$(printf '%s\n' $esperado | sort | tr '\n' ' ' | sed 's/ *$//')
  if [ "$obtido" = "$esperado" ]; then
    echo "  OK    $nome"
  else
    echo "  FALHA $nome"
    echo "        esperado: [${esperado}]"
    echo "        obtido:   [${obtido}]"
    FALHAS=$((FALHAS + 1))
  fi
}

# Guarda que vale mais que todas as outras: se um dia alguem inverter uma
# condicao, este teste e o que grita.
nunca() {
  local nome="$1" proibida="$2"
  if grep -qx "$proibida" "$REMOVIDAS" 2>/dev/null; then
    echo "  FALHA $nome  <<< REMOVEU '$proibida'"
    FALHAS=$((FALHAS + 1))
  else
    echo "  OK    $nome  (nao removeu '$proibida')"
  fi
}

echo "============================================================"
echo " harness da podar_imagens()  —  FABIANO-81"
echo "============================================================"
KEEP_IMAGENS=5

# --- 1: o caso de producao de hoje -------------------------------------------
echo
echo "[1] 20 tags, KEEP=5, container rodando a mais nova"
TAGS="t01 t02 t03 t04 t05 t06 t07 t08 t09 t10 t11 t12 t13 t14 t15 t16 t17 t18 previous latest"
IDS="t01:sha01 t02:sha02 t03:sha03 t04:sha04 t05:sha05 t06:sha06 t07:sha07 t08:sha08 t09:sha09 t10:sha10 t11:sha11 t12:sha12 t13:sha13 t14:sha14 t15:sha15 t16:sha16 t17:sha17 t18:sha18 previous:sha02 latest:sha01"
EM_USO="sha01"; TAG_NOVA="t01"; TAG_ANTERIOR="previous"
caso "remove da 6a em diante, preserva as 5 mais novas" \
     "t07 t08 t09 t10 t11 t12 t13 t14 t15 t16 t17 t18"
nunca "protege a rede do rollback" previous
nunca "protege o ponteiro do registry" latest
nunca "protege a tag do deploy corrente" t01

# --- 2: imagem em uso e VELHA ------------------------------------------------
# Acontece de verdade: rollback deixa o container rodando uma imagem antiga
# enquanto tags mais novas continuam na maquina.
echo
echo "[2] container rodando uma imagem VELHA (pos-rollback)"
EM_USO="sha14"; TAG_NOVA="t01"; TAG_ANTERIOR="previous"
caso "pula a imagem em uso mesmo estando na cauda" \
     "t07 t08 t09 t10 t11 t12 t13 t15 t16 t17 t18"
nunca "nao apaga o chao onde o container esta pisando" t14

# --- 3: primeiro deploy ------------------------------------------------------
echo
echo "[3] primeiro deploy: nenhum container de pe, so 2 tags"
TAGS="t01 latest"; IDS="t01:sha01 latest:sha01"
EM_USO=""; TAG_NOVA="t01"; TAG_ANTERIOR=""
caso "nao remove nada e nao quebra" ""

# --- 4: menos tags que o KEEP ------------------------------------------------
echo
echo "[4] 4 tags com KEEP=5"
TAGS="t01 t02 t03 t04 previous"; IDS="t01:sha01 t02:sha02 t03:sha03 t04:sha04 previous:sha02"
EM_USO="sha01"; TAG_NOVA="t01"; TAG_ANTERIOR="previous"
caso "nao remove nada" ""

# --- 5: a fronteira ----------------------------------------------------------
# KEEP=5 significa "5 versoes ALEM das protegidas". Como a TAG_NOVA e sempre a
# mais nova e e protegida, a maquina fica de fato com 6 imagens do backend.
echo
echo "[5] fronteira: 6 tags com KEEP=5 (a mais nova e a TAG_NOVA)"
TAGS="t01 t02 t03 t04 t05 t06"; IDS="t01:s1 t02:s2 t03:s3 t04:s4 t05:s5 t06:s6"
EM_USO="s1"; TAG_NOVA="t01"; TAG_ANTERIOR=""
caso "5 candidatas com KEEP=5: nao remove nada" ""

echo
echo "[5b] uma tag a mais: agora sobra exatamente uma para remover"
TAGS="t01 t02 t03 t04 t05 t06 t07"
IDS="t01:s1 t02:s2 t03:s3 t04:s4 t05:s5 t06:s6 t07:s7"
EM_USO="s1"; TAG_NOVA="t01"; TAG_ANTERIOR=""
caso "remove so a mais velha" "t07"

# --- 6: a :previous nao consome vaga -----------------------------------------
# Se o filtro de protegidas viesse DEPOIS do tail, 'previous' ocuparia uma das 5
# vagas e t06 sairia junto com t07.
echo
echo "[6] :previous no meio da lista nao consome vaga do KEEP"
TAGS="t01 t02 previous t03 t04 t05 t06 t07"
IDS="t01:s1 t02:s2 previous:s2 t03:s3 t04:s4 t05:s5 t06:s6 t07:s7"
EM_USO="s1"; TAG_NOVA="t01"; TAG_ANTERIOR="previous"
caso "guarda t02..t06 e so remove t07" "t07"
nunca "previous continua intacta" previous

# --- 7: tags dangling <none> -------------------------------------------------
echo
echo "[7] tag <none> nao vira 'docker rmi imagem:<none>'"
TAGS="t01 t02 t03 t04 t05 <none> t07 t08"
IDS="t01:s1 t02:s2 t03:s3 t04:s4 t05:s5 t07:s7 t08:s8"
EM_USO="s1"; TAG_NOVA="t01"; TAG_ANTERIOR=""
caso "ignora <none> e remove so a cauda real" "t08"
nunca "nao tentou remover a dangling pelo nome" "<none>"

echo
echo "============================================================"
if [ "$FALHAS" = "0" ]; then
  echo " TODOS OS CASOS PASSARAM"
else
  echo " ${FALHAS} FALHA(S) — nao colar no deploy-safe.sh"
fi
echo "============================================================"
exit "$FALHAS"
