#!/bin/bash
# =============================================================================
# Varredura operacional — exercita o que deveria funcionar sozinho
# =============================================================================
# POR QUE ESTE SCRIPT EXISTE
#
# Em 12/08/2026 a producao migrou para Auto Scaling e, nas doze horas seguintes,
# apareceram 25 defeitos. Nenhum foi encontrado por monitoramento: todos
# apareceram quando alguem tentou usar a funcao.
#
#   O backup so falha as 3h30. O certificado so falha ao renovar.
#   O deploy so falha ao deployar.
#
# Uma maquina feita a mao acumula estado que ninguem declarou — um diretorio
# criado num plantao, uma linha no crontab, um arquivo de credencial, uma
# variavel exportada no ambiente. A maquina descartavel nao herda nada, e cada
# ausencia dorme ate a funcao ser exercida.
#
# Este script adianta todos os relogios de proposito. Rodar depois de QUALQUER
# recriacao de maquina, e antes de confiar que esta tudo certo.
#
# USO:  sudo ./varredura.sh          (na maquina, via SSM ou SSH)
# SAI:  0 se tudo OK, 1 se houver qualquer [FALHA]
# =============================================================================
set -uo pipefail

FALHAS=0
ok()    { echo "[OK]    $1"; }
falha() { echo "[FALHA] $1"; FALHAS=$((FALHAS + 1)); }

DEPLOY_DIR=${DEPLOY_DIR:-/home/ec2-user/fabiano}
echo "########## $(hostname) — $(date '+%Y-%m-%d %H:%M:%S') ##########"

# --- IP publico, base para decidir quais dominios sao "meus" -----------------
TOKEN=$(curl -s -X PUT http://169.254.169.254/latest/api/token \
  -H "X-aws-ec2-metadata-token-ttl-seconds: 120" 2>/dev/null)
MEU_IP=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" \
  http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null)
[ -n "$MEU_IP" ] && ok "IP publico: $MEU_IP" || falha "nao consegui ler o IP publico (IMDS)"

meu_dominio() {
  local ips
  ips=$(getent ahostsv4 "$1" 2>/dev/null | awk '{print $1}' | sort -u | tr '\n' ' ')
  case " $ips " in *" $MEU_IP "*) return 0 ;; *) return 1 ;; esac
}

# --- containers --------------------------------------------------------------
N=$(docker ps --format '{{.Names}}' 2>/dev/null | wc -l)
RUIM=$(docker ps --filter health=unhealthy --format '{{.Names}}' 2>/dev/null | tr '\n' ' ')
if [ "$N" -ge 8 ] && [ -z "$RUIM" ]; then
  ok "containers: $N no ar, nenhum unhealthy"
else
  falha "containers: $N no ar | unhealthy: ${RUIM:-nenhum}"
fi

docker network inspect fabiano-internal >/dev/null 2>&1 \
  && ok "rede fabiano-internal existe" \
  || falha "rede fabiano-internal AUSENTE (o compose da aplicacao e quem a cria)"

# --- certificados ------------------------------------------------------------
# Autoassinado so e problema no dominio que aponta para ESTA maquina. Os outros
# sao pontes de proposito: sem elas o nginx nao inicia.
for C in /etc/letsencrypt/live/*/fullchain.pem; do
  [ -f "$C" ] || continue
  D=$(basename "$(dirname "$C")")
  EM=$(openssl x509 -issuer -noout -in "$C" 2>/dev/null | sed 's/.*CN *= *//')
  DIAS=$(( ( $(date -d "$(openssl x509 -enddate -noout -in "$C" | cut -d= -f2)" +%s) - $(date +%s) ) / 86400 ))
  AUTO=0; case "$EM" in *"$D"*) AUTO=1 ;; esac
  if meu_dominio "$D"; then
    [ "$AUTO" -eq 1 ] && falha "cert $D: AUTOASSINADO e o dominio aponta para ca" \
                      || { [ "$DIAS" -lt 21 ] && falha "cert $D: vence em $DIAS dias" \
                                              || ok "cert $D: $EM, $DIAS dias"; }
  else
    ok "cert $D: ponte (dominio nao aponta para esta maquina)"
  fi
done

certbot renew --dry-run >/tmp/varredura-dry.log 2>&1 \
  && ok "certbot renew --dry-run" \
  || falha "certbot renew --dry-run: $(grep -aiE 'error|failed' /tmp/varredura-dry.log | tail -1)"

# --- agendamentos ------------------------------------------------------------
systemctl is-active crond >/dev/null 2>&1 && ok "crond ativo" || falha "crond parado"
for J in backup-db renovar-certificados; do
  grep -rqs "$J" /etc/cron.d/ /var/spool/cron/ \
    && ok "cron: $J agendado" \
    || falha "cron: $J SEM agendamento"
done

# --- scripts e permissoes ----------------------------------------------------
for S in backup-db.sh renovar-certificados.sh; do
  [ -x "/app/$S" ] && ok "/app/$S ($(wc -c < "/app/$S") bytes)" || falha "/app/$S AUSENTE"
done
# O deploy roda como ec2-user e grava o dump pre-migration aqui.
if sudo -u ec2-user test -w /app/backups/pre-deploy 2>/dev/null; then
  ok "/app/backups/pre-deploy gravavel pelo ec2-user"
else
  falha "/app/backups/pre-deploy nao gravavel pelo ec2-user (o deploy morre acusando o mysqldump)"
fi

# --- variaveis: SO NOMES, nunca valores --------------------------------------
if grep -qs '^SMTP_USER=' /etc/fabiano-backup.env; then
  for V in SMTP_HOST SMTP_PORT SMTP_USER SMTP_PASS MAIL_TO; do
    grep -qs "^$V=" /etc/fabiano-backup.env && ok "backup.env: $V" || falha "backup.env: $V AUSENTE"
  done
else
  ok "backup.env sem SMTP (esperado fora de producao)"
fi
for V in METRICS_SCRAPE_TOKEN FABIANO_BACKUP_BUCKET BACKEND_TAG; do
  grep -qs "^$V=" "$DEPLOY_DIR/deploy/.env" && ok "deploy/.env: $V" || falha "deploy/.env: $V AUSENTE"
done

# O bind mount cria um DIRETORIO vazio quando o caminho nao existe, e o
# Prometheus tenta ler o diretorio como token: backend responde 401 e o painel
# fica PARADA, sem erro em lugar nenhum.
TK="$DEPLOY_DIR/deploy/observability/metrics-token"
if [ -f "$TK" ] && [ -s "$TK" ]; then ok "metrics-token e arquivo com conteudo"
elif [ -d "$TK" ]; then falha "metrics-token e DIRETORIO (bind mount criou vazio)"
else falha "metrics-token ausente ou vazio"; fi

# --- prometheus --------------------------------------------------------------
if docker exec fabiano-prometheus wget -qO- http://localhost:9090/api/v1/targets \
     > /tmp/varredura-alvos.json 2>/dev/null; then
  python3 - <<'PY' || true
import json
d = json.load(open('/tmp/varredura-alvos.json'))
for t in d['data']['activeTargets']:
    j = t['labels'].get('job')
    print(f"[OK]    alvo {j}" if t['health'] == 'up'
          else f"[FALHA] alvo {j}: {(t.get('lastError') or '')[:70]}")
PY
  grep -q '"health":"down"' /tmp/varredura-alvos.json && FALHAS=$((FALHAS + 1))
  # O backend NAO publica porta no host — quem fala com ele e o nginx pela rede
  # do Docker. Testar localhost:8080 daria falso positivo; o alvo do Prometheus
  # e a prova correta de que ele esta de pe.
  grep -q '"job":"fabiano-back".*"health":"up"' /tmp/varredura-alvos.json \
    && ok "backend saudavel (via alvo do Prometheus)" \
    || falha "backend nao esta sendo raspado com sucesso"
else
  falha "prometheus nao respondeu"
fi

# --- metricas de arquivo -----------------------------------------------------
for M in backup_fabiano certbot_fabiano; do
  A=/var/lib/node_exporter/textfile_collector/$M.prom
  [ -s "$A" ] && ok "metrica $M ($(grep -vc '^#' "$A") series)" || falha "metrica $M AUSENTE"
done
grep -qs 'certbot_fabiano_timestamp_seconds' \
  /var/lib/node_exporter/textfile_collector/certbot_fabiano.prom \
  && ok "metrica de ultima renovacao presente" \
  || falha "metrica de ultima renovacao AUSENTE (painel fica SEM DADO)"

# --- acesso da esteira -------------------------------------------------------
L=$(wc -l < /home/ec2-user/.ssh/authorized_keys 2>/dev/null || echo 0)
[ "$L" -ge 1 ] && ok "authorized_keys: $L chave(s)" \
               || falha "authorized_keys VAZIO — a esteira nao consegue entrar"

# --- disco -------------------------------------------------------------------
U=$(df --output=pcent / | tail -1 | tr -dc '0-9')
[ "$U" -lt 80 ] && ok "disco: $U% usado" || falha "disco: $U% usado"

# --- HTTPS visto de dentro, sem -k -------------------------------------------
for D in $(ls /etc/letsencrypt/live 2>/dev/null | grep '\.'); do
  meu_dominio "$D" || continue
  CODE=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 \
    --resolve "$D:443:127.0.0.1" "https://$D/" 2>/dev/null)
  [ "$CODE" != "000" ] && ok "https $D -> $CODE (cadeia valida)" \
                       || falha "https $D nao respondeu com cadeia valida"
done

echo "----------------------------------------"
[ "$FALHAS" -eq 0 ] && echo "VARREDURA OK — nenhuma falha" || echo "VARREDURA: $FALHAS falha(s)"
exit $([ "$FALHAS" -eq 0 ] && echo 0 || echo 1)
