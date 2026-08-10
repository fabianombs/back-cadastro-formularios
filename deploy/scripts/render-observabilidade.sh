#!/usr/bin/env bash
# =============================================================================
# Ajusta a observabilidade para o ambiente onde ela vai rodar (FABIANO-33)
# =============================================================================
# ONDE RODA: na maquina alvo, pelo job de deploy, DEPOIS do scp e ANTES de os
# containers de observabilidade serem recriados.
#
# POR QUE EXISTE
#
# O job de deploy manda 'deploy/**' inteiro por scp, de proposito: o repositorio
# e a fonte da verdade e ajuste feito a mao no servidor deve sumir mesmo. So que
# 'deploy/observability/' descreve O QUE VIGIAR — e isso e diferente em cada
# ambiente.
#
# Em 10/08/2026 isso apareceu do pior jeito possivel. O user-data da homolog
# retargeava o prometheus.yml para os nomes de hml, e 59 segundos depois o job
# 'Deploy em homologacao' escrevia a versao de producao por cima. Resultado:
#
#   - o Grafana da homolog vigiava a PRODUCAO, verde e confiante, sem dizer nada
#     sobre a maquina que estava sendo testada;
#   - os logs da homolog entravam etiquetados 'ambiente: prod', misturados aos
#     de producao para quem filtrasse por ambiente num incidente.
#
# Nenhum dos dois dava erro. O painel ficava verde porque estava olhando para o
# lugar errado — que e a forma mais cara de estar errado.
#
# POR QUE A TRANSFORMACAO, E NAO UM ARQUIVO DE TEMPLATE
#
# Marcadores do tipo ${DOMINIO_API} obrigariam PRODUCAO a renderizar tambem. Se
# um dia o caminho de producao esquecer de chamar este script, o Prometheus de
# producao passa a sondar a string literal '${DOMINIO_API}' e o alarme de site
# fora do ar dispara sem que nada tenha caido.
#
# Mantendo o arquivo do repositorio valido POR SI SO para producao, o caminho de
# producao nao depende deste script para estar correto. Homolog e que declara a
# diferenca — e a diferenca fica em um lugar so, versionada e revisavel.
#
# A VERIFICACAO E A PARTE QUE IMPORTA
#
# Renderizar sem conferir so troca um erro silencioso por outro. Por isso o
# script termina abortando se sobrar qualquer nome de producao no arquivo. Um
# sed que nao casou nada devolve exit 0 e um arquivo intacto: sem a conferencia
# final, a falha seria exatamente a de hoje, de novo.
# =============================================================================
set -euo pipefail

AMBIENTE="${1:-}"
[ -n "$AMBIENTE" ] || { echo "uso: render-observabilidade.sh <prod|homolog>"; exit 1; }

OBS="$(cd "$(dirname "$0")/.." && pwd)/observability"
[ -d "$OBS" ] || { echo "ERRO: nao encontrei ${OBS}"; exit 1; }

PROM="$OBS/prometheus.yml"
PROMTAIL="$OBS/promtail-config.yml"

# 'cat >' e nao 'sed -i': esses arquivos sao montados um a um dentro dos
# containers, e sed -i troca o inode. O container continuaria lendo o arquivo
# antigo, e o log nao diria nada.
escrever() { cat > "$2" < "$1"; rm -f "$1"; }

case "$AMBIENTE" in

  prod)
    # Nao e um no-op silencioso: e uma CONFERENCIA.
    #
    # O desenho deste script depende de o arquivo do repositorio ser valido por
    # si so para producao. Em 10/08/2026 essa premissa estava falsa sem ninguem
    # saber: o external_labels dizia 'ambiente: homolog' na producao, resto da
    # virada de 08/08 em que a maquina nova rodou como homolog antes de assumir.
    # Conferir aqui e o que impede a premissa de apodrecer de novo em silencio.
    if ! grep -qE '^ *ambiente: *prod *$' "$PROM"; then
      echo "ERRO: o prometheus.yml do repositorio nao declara 'ambiente: prod'."
      echo "      Este arquivo vai para PRODUCAO como esta. Corrija no repositorio."
      grep -nE '^ *ambiente:' "$PROM" || true
      exit 1
    fi
    echo "ambiente=prod: arquivos do repositorio conferidos, nada a renderizar"
    ;;

  homolog)
    echo "ambiente=homolog: reapontando a observabilidade para os nomes de hml"

    sed -e 's|https://api\.nexventa\.com\.br|https://api-hml.nexventa.com.br|g' \
        -e 's|https://grafana\.nexventa\.com\.br|https://grafana-hml.nexventa.com.br|g' \
        -e '/sslip\.io/d' \
        -e 's|^\( *ambiente: *\)prod *$|\1homolog|' \
        "$PROM" > "$PROM.novo"
    escrever "$PROM.novo" "$PROM"

    # A linha do sslip.io e APAGADA, nao reapontada: aquele nome carrega o IP
    # fixo da producao. Reapontar para o IP da homolog nao serviria — o nginx
    # que veio na AMI nao tem server block para esse nome, e a sonda cairia no
    # servidor padrao, medindo outra coisa e chamando de sucesso.

    sed -e "s|^\( *replacement: *\)'prod'|\1'homolog'|" \
        -e 's|^\( *ambiente: *\)prod *$|\1homolog|' \
        "$PROMTAIL" > "$PROMTAIL.novo"
    escrever "$PROMTAIL.novo" "$PROMTAIL"

    # --- conferencia: sem isto, um sed que nao casou nada passaria batido -----
    FALHAS=0

    if grep -nE 'https://(api|grafana)\.nexventa\.com\.br|sslip\.io' "$PROM"; then
      echo "ERRO: sobrou alvo de PRODUCAO no prometheus.yml (linhas acima)."
      FALHAS=1
    fi

    if grep -nE "^ *(replacement: *'prod'|ambiente: *prod *)$" "$PROMTAIL"; then
      echo "ERRO: sobrou 'ambiente: prod' no promtail-config.yml (linhas acima)."
      echo "      Os logs da homolog entrariam misturados aos de producao."
      FALHAS=1
    fi

    if ! grep -qE '^ *ambiente: *homolog *$' "$PROM"; then
      echo "ERRO: o external_labels do prometheus.yml nao ficou 'ambiente: homolog'."
      echo "      As series da homolog sairiam carimbadas como producao."
      FALHAS=1
    fi

    if ! grep -q 'api-hml\.nexventa\.com\.br' "$PROM"; then
      echo "ERRO: o prometheus.yml nao tem nenhum alvo de hml depois de renderizar."
      echo "      Provavel: o formato do arquivo no repositorio mudou e o sed nao casou."
      FALHAS=1
    fi

    [ "$FALHAS" -eq 0 ] || exit 1

    echo "ok: observabilidade apontando para homolog"
    grep -nE 'https://|ambiente:' "$PROM" "$PROMTAIL" | grep -vE '^\S+: *[0-9]+: *#'
    ;;

  *)
    echo "ERRO: ambiente '${AMBIENTE}' desconhecido. Use 'prod' ou 'homolog'."
    exit 1
    ;;
esac
