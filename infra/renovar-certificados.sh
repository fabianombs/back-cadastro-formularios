#!/usr/bin/env bash
# =============================================================================
# Renovacao de certificados TLS — producao e homolog (FABIANO-76)
# =============================================================================
# POR QUE ESTE ARQUIVO EXISTE
#
# A maquina antiga tinha "0 3 * * * certbot renew" no crontab do root. A virada
# de 08/08/2026 trouxe o Docker Compose e deixou o agendamento para tras: a
# maquina nova ficou SEM RENOVACAO NENHUMA. Descoberto em 09/08/2026, com os
# certificados de producao vencendo em 06/11. Seria o incidente de 10/07 se
# repetindo no mesmo sistema, quatro meses depois.
#
# DOIS DETALHES QUE UM 'certbot renew' SIMPLES NAO RESOLVE
#
# 1. Nem todo certificado no disco pertence a esta maquina. Alem dos nomes de
#    producao, ha api-hml e grafana-hml, que apontam para o Elastic IP de
#    homolog. Renova-los daqui falha TODA NOITE — e alerta que falha todo dia e
#    alerta que se aprende a ignorar.
#
#    Medido em 09/08 com 'certbot renew --dry-run':
#      sucesso: 100-30-35-83.sslip.io, api., grafana.
#      falha:   api-hml, grafana-hml  (Timeout during connect em 54.197.175.159)
#
# 2. O nginx roda em CONTAINER, entao o certificado novo precisa de um reload
#    para sair do disco e chegar na memoria do processo.
#
#    ISSO JA ESTAVA RESOLVIDO, e este script NAO deve mexer:
#      /etc/letsencrypt/renewal-hooks/deploy/10-reload-nginx-container.sh
#    roda depois de QUALQUER renovacao bem-sucedida, de qualquer certificado,
#    inclusive dos que ainda nao existem — por isso mora no diretorio e nao no
#    .conf de cada um.
#
#    Correcao de 09/08/2026: a primeira versao deste script passava
#    '--deploy-hook' na linha de comando. O certbot PERSISTE esse valor como
#    'renew_hook' dentro do .conf do certificado — ou seja, o script teria ido
#    escrevendo hook redundante nos cinco arquivos, um por noite, recriando a
#    divergencia que o autor do hook de diretorio evitou de proposito. Removido.
#
# A ESCOLHA DE DESENHO
#
# Em vez de uma lista fixa de nomes, o script pergunta a cada certificado se o
# dominio dele resolve para o IP publico DESTA maquina. Consequencia: o mesmo
# arquivo, sem edicao, faz a coisa certa em producao E em homolog — cada uma
# renova exatamente o que aponta para ela. Uma lista fixa exigiria lembrar de
# edita-la, e ninguem lembra.
# =============================================================================
set -uo pipefail

LOG=/var/log/renovar-certificados.log
METRICA=/var/lib/node_exporter/textfile_collector/certbot_fabiano.prom

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG"; }

log "=== inicio ==="

# IMDSv2: o AL2023 exige token. A versao sem token (IMDSv1) responderia 401 e o
# script concluiria que nao tem IP publico — renovando nada, em silencio.
TOKEN=$(curl -sf -X PUT "http://169.254.169.254/latest/api/token" \
  -H "X-aws-ec2-metadata-token-ttl-seconds: 60" 2>/dev/null || true)
MEU_IP=$(curl -sf -H "X-aws-ec2-metadata-token: ${TOKEN}" \
  "http://169.254.169.254/latest/meta-data/public-ipv4" 2>/dev/null || true)

if [ -z "${MEU_IP}" ]; then
  log "ERRO: nao consegui descobrir o IP publico desta maquina pelo metadata."
  log "      Sem isso nao da para saber quais certificados sao daqui. Abortando."
  exit 1
fi
log "IP publico desta maquina: ${MEU_IP}"

resolver() { getent ahostsv4 "$1" 2>/dev/null | awk '{print $1; exit}'; }

TOTAL=0
MEUS=0
ALHEIOS=0
FALHAS=0

for conf in /etc/letsencrypt/renewal/*.conf; do
  [ -e "$conf" ] || continue
  NOME=$(basename "$conf" .conf)
  TOTAL=$((TOTAL + 1))

  IP_DOMINIO=$(resolver "$NOME")

  if [ "$IP_DOMINIO" != "$MEU_IP" ]; then
    # Nao e erro: e um certificado de outra maquina que veio junto na AMI.
    log "pulando ${NOME} — aponta para ${IP_DOMINIO:-<nao resolve>}, nao para mim"
    ALHEIOS=$((ALHEIOS + 1))
    continue
  fi

  MEUS=$((MEUS + 1))
  log "renovando ${NOME}"

  # SEM --deploy-hook aqui, de proposito. O certbot persistiria o valor como
  # 'renew_hook' dentro do .conf, e o reload ja e feito pelo hook de diretorio
  # (renewal-hooks/deploy/10-reload-nginx-container.sh), que vale para todos os
  # certificados sem precisar ser declarado em cada um.
  if certbot renew \
       --cert-name "$NOME" \
       --quiet \
       >>"$LOG" 2>&1; then
    log "  ok"
  else
    log "  FALHOU — ver /var/log/letsencrypt/letsencrypt.log"
    FALHAS=$((FALHAS + 1))
  fi
done

log "resumo: ${TOTAL} certificados no disco, ${MEUS} meus, ${ALHEIOS} de outra maquina, ${FALHAS} falhas"

# Dead man's switch, no mesmo padrao do backup: grava QUANDO rodou, nao se deu
# certo. Renovacao que para de rodar nao gera erro nenhum — so ausencia, que e
# exatamente o que ninguem percebe.
if [ -d "$(dirname "$METRICA")" ]; then
  cat > "${METRICA}.tmp" <<EOF
# HELP certbot_fabiano_timestamp_seconds momento da ultima execucao da renovacao
# TYPE certbot_fabiano_timestamp_seconds gauge
certbot_fabiano_timestamp_seconds $(date +%s)
# HELP certbot_fabiano_falhas numero de certificados que falharam na ultima execucao
# TYPE certbot_fabiano_falhas gauge
certbot_fabiano_falhas ${FALHAS}
# HELP certbot_fabiano_gerenciados certificados que esta maquina renova
# TYPE certbot_fabiano_gerenciados gauge
certbot_fabiano_gerenciados ${MEUS}
EOF
  # mv atomico: o node_exporter pode estar lendo o arquivo neste instante, e
  # ler um .prom pela metade faz a metrica sumir por um ciclo.
  mv "${METRICA}.tmp" "${METRICA}"
fi

log "=== fim ==="
exit 0
