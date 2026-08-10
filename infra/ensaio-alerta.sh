#!/usr/bin/env bash
# =============================================================================
# FABIANO-64 / FABIANO-3 — provar que o alerta DISPARA de verdade
# =============================================================================
# A regra 'Site fora do ar visto de fora' tem for: 2m e avaliacao a cada 1m.
# Por isso a queda de 100s do ensaio anterior nao bastou: ela mostrou a metrica
# cair, mas nao a regra mudar de estado.
#
# Roda SO na homolog. O trap religa o nginx aconteca o que acontecer.
set -uo pipefail
trap 'echo; echo ">>> religando o nginx"; docker start fabiano-nginx >/dev/null 2>&1; sleep 5; docker ps --filter name=fabiano-nginx --format "{{.Names}} {{.Status}}"' EXIT

# Trava: este script derruba o servico. Se por engano rodar na producao, para.
if ! grep -q '^DB_HOST=.*homolog' /home/ec2-user/fabiano/deploy/.env 2>/dev/null; then
  echo "ABORTANDO: esta maquina nao parece ser homolog."
  exit 1
fi

set -a; . /home/ec2-user/fabiano/deploy/.env; set +a

estado() {
  docker exec fabiano-grafana curl -sS -u "$GRAFANA_ADMIN_USER:$GRAFANA_ADMIN_PASSWORD" \
    http://localhost:3000/api/prometheus/grafana/api/v1/rules 2>/dev/null \
  | python3 -c "
import sys, json
try:
    grupos = json.load(sys.stdin)['data']['groups']
except Exception as erro:
    print('    (nao consegui ler o estado: ' + str(erro) + ')')
    raise SystemExit
achou = False
for g in grupos:
    for r in g.get('rules', []):
        nome = r.get('name', '')
        if 'fora do ar visto de fora' in nome or 'Certificado HTTPS' in nome:
            achou = True
            print('    ' + nome + ': ' + str(r.get('state', '?')).upper())
if not achou:
    print('    (nenhuma regra externa encontrada)')
"
}

echo "=== ANTES ==="
estado

echo
echo ">>> parando o nginx (a homolog fica fora do ar por ~4 min)"
docker stop fabiano-nginx >/dev/null

for i in 1 2 3 4; do
  sleep 60
  echo "--- t+${i}min ---"
  estado
done
