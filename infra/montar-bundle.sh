#!/usr/bin/env bash
# =============================================================================
#  Monta o pacote de deploy que uma maquina NOVA baixa no boot (FABIANO-58).
#
#  A esteira de hoje entrega os arquivos por scp para uma maquina que ja existe.
#  Uma instancia criada pelo Auto Scaling as 3h da manha nao tem endereco
#  conhecido nem chave autorizada — ninguem faz scp para ela. Ela precisa
#  BUSCAR o pacote, e este script e quem o produz.
#
#  Uso:
#     ./infra/montar-bundle.sh [caminho-de-saida]
#
#  Rodar sempre a partir da raiz do repositorio.
# =============================================================================
set -euo pipefail

SAIDA="${1:-deploy-bundle.tar.gz}"

# O conteudo e definido aqui, uma vez, e conferido no fim. O user_data extrai
# este tar direto em $DEPLOY_DIR e espera encontrar deploy/, observability/ e
# infra/ na raiz — por isso os caminhos sao relativos a raiz do repo.
CONTEUDO=(
  deploy
  observability
  infra/backup-db.sh
  infra/enviar-backup-email.py
  # Faltava ate 10/08/2026. Uma maquina nascida do bundle vinha SEM rotina de
  # renovacao de certificado — o mesmo buraco do FABIANO-76, que apareceu na
  # virada de 08/08 e teria voltado no primeiro boot pelo Auto Scaling.
  infra/renovar-certificados.sh
  # A auditoria operacional precisa viajar com o bundle: ela e o que transforma
  # "parece que esta tudo bem" em uma lista fechada de OK e FALHA. Deixa-la fora
  # do pacote e o mesmo que nao ter — ninguem copia script a mao no meio de um
  # incidente.
  infra/varredura.sh
)

# Sem isto, um arquivo renomeado sairia do pacote em silencio e so apareceria
# como falha de boot de uma maquina nova, semanas depois.
for alvo in "${CONTEUDO[@]}"; do
  [ -e "$alvo" ] || { echo "ABORTADO: '$alvo' nao existe. Rode a partir da raiz do repositorio."; exit 1; }
done

# Segredo nao viaja no pacote. O bundle fica num bucket e e lido por qualquer
# instancia com a role; senha vem do Parameter Store, que e outro caminho de
# permissao de proposito. Esta checagem existe porque um .env esquecido no
# working tree entraria calado — o .gitignore protege o git, nao o tar.
# O .env.example e a excecao legitima: e documentacao, nao tem valor real.
ACHADOS=$(find "${CONTEUDO[@]}" \( -name '.env' -o -name '.env.*' \) ! -name '.env.example' 2>/dev/null)
if [ -n "$ACHADOS" ]; then
  echo "ABORTADO: ha arquivo .env dentro do conteudo do pacote."
  echo "$ACHADOS"
  echo "Segredo vai para o Parameter Store, nunca para o bucket de artefatos."
  exit 1
fi

echo ">>> Montando $SAIDA"
tar -czf "$SAIDA" \
  --exclude='.env' \
  --exclude='.env.*' \
  --exclude='*.log' \
  "${CONTEUDO[@]}"

# Conferir o que entrou, em vez de presumir — criterio de aceite do FABIANO-58.
echo ">>> Conferindo o conteudo"
LISTA=$(tar -tzf "$SAIDA")

falta=0
for esperado in \
  "deploy/docker-compose.yml" \
  "deploy/scripts/deploy-safe.sh" \
  "deploy/nginx/nginx.conf" \
  "observability/prometheus/prometheus.yml" \
  "infra/backup-db.sh" \
  "infra/enviar-backup-email.py" \
  "infra/renovar-certificados.sh" \
  "infra/varredura.sh"
do
  if grep -qx "$esperado" <<< "$LISTA"; then
    echo "    ok   $esperado"
  else
    echo "    FALTA $esperado"
    falta=1
  fi
done

[ "$falta" -eq 0 ] || { echo "ABORTADO: pacote incompleto."; exit 1; }

echo ">>> $(tar -tzf "$SAIDA" | wc -l) arquivos, $(du -h "$SAIDA" | cut -f1)"
echo ">>> $SAIDA pronto"
