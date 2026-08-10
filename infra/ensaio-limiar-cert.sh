#!/usr/bin/env bash
# =============================================================================
# FABIANO-64, ultimo criterio — o alerta de certificado disparando PELO LIMIAR
# =============================================================================
# O ensaio anterior mostrou essa regra ir para PENDING, mas por AUSENCIA de
# dado (noDataState: Alerting), nao por comparacao. Sao mecanismos diferentes:
# um prova que silencio vira alarme, o outro prova que a conta esta certa.
#
# O card pede exatamente isto: "testavel baixando o limiar para um valor que a
# data atual ja viole, e devolvendo depois".
#
# A regra hoje: (probe_ssl_earliest_cert_expiry - time()) / 86400  <  14
# Os certificados tem ~90 dias, entao com limiar 14 nunca dispara. Subindo o
# limiar para 999, a condicao passa a ser verdadeira agora.
#
# Alem do limiar, o 'for' cai de 15m para 10s — senao o ensaio levaria 15
# minutos para provar uma comparacao numerica.
#
# RESTAURACAO: o arquivo original e guardado e devolvido no fim, pelo trap.
# E, mesmo que tudo desse errado, o proximo deploy da develop reescreve
# observability/ inteiro a partir do repositorio.
# =============================================================================
set -uo pipefail

REGRAS=/home/ec2-user/fabiano/observability/grafana/provisioning/alerting/rules.yaml
BACKUP=/tmp/rules.yaml.original

if ! grep -q '^DB_HOST=.*homolog' /home/ec2-user/fabiano/deploy/.env 2>/dev/null; then
  echo "ABORTANDO: esta maquina nao parece ser homolog."
  exit 1
fi
[ -f "$REGRAS" ] || { echo "ABORTANDO: nao achei $REGRAS"; exit 1; }

restaurar() {
  echo
  echo ">>> devolvendo a regra original"
  cp "$BACKUP" "$REGRAS"
  docker restart fabiano-grafana >/dev/null
  sleep 20
  docker exec fabiano-nginx nginx -s reload >/dev/null 2>&1
  echo ">>> restaurado (limiar de volta em 14 dias, for de volta em 15m)"
}
trap restaurar EXIT

cp "$REGRAS" "$BACKUP"

set -a; . /home/ec2-user/fabiano/deploy/.env; set +a

estado() {
  docker exec fabiano-grafana curl -sS -u "$GRAFANA_ADMIN_USER:$GRAFANA_ADMIN_PASSWORD" \
    http://localhost:3000/api/prometheus/grafana/api/v1/rules 2>/dev/null \
  | python3 -c "
import sys, json
try:
    grupos = json.load(sys.stdin)['data']['groups']
except Exception as erro:
    print('    (nao consegui ler: ' + str(erro) + ')')
    raise SystemExit
for g in grupos:
    for r in g.get('rules', []):
        if 'Certificado HTTPS' in r.get('name', ''):
            print('    ' + r['name'] + ': ' + str(r.get('state', '?')).upper())
"
}

echo "=== ANTES (limiar 14 dias, certificados com ~90) ==="
estado

python3 - "$REGRAS" <<'PY'
import sys, yaml
caminho = sys.argv[1]
d = yaml.safe_load(open(caminho, encoding='utf-8'))
mexeu = 0
for g in d['groups']:
    for r in g.get('rules', []):
        if 'Certificado HTTPS' in r.get('title', ''):
            r['for'] = '10s'
            for q in r['data']:
                for c in q.get('model', {}).get('conditions', []):
                    ev = c.get('evaluator')
                    if ev and ev.get('type') == 'lt':
                        ev['params'] = [999]
                        mexeu += 1
yaml.safe_dump(d, open(caminho, 'w', encoding='utf-8'), allow_unicode=True, sort_keys=False)
print('>>> limiar trocado para 999 dias em ' + str(mexeu) + ' condicao(oes), for=10s')
PY

echo ">>> recarregando o Grafana com a regra alterada"
docker restart fabiano-grafana >/dev/null
sleep 25
docker exec fabiano-nginx nginx -s reload >/dev/null 2>&1

for i in 1 2 3; do
  sleep 45
  echo "--- t+$((i*45))s com limiar 999 ---"
  estado
done
