#!/usr/bin/env bash
# =============================================================================
# checar-29-30.sh                              (FABIANO-29 e FABIANO-30)
#
# SOMENTE LEITURA. Nao instala, nao cria, nao apaga, nao reinicia nada.
# Feito para rodar NA PRODUCAO — cada comando aqui e um 'ls', um 'stat', um
# 'grep' ou um 'crontab -l'.
#
# FABIANO-29  o backup pre-deploy funciona de verdade? (mysqldump existe, o
#             dump tem tamanho, tem 'Dump completed' e tem CREATE TABLE)
# FABIANO-30  os sete itens do checklist de pendencias manuais
# =============================================================================
set -uo pipefail

echo "============================================================"
echo " maquina: $(hostname)   $(TZ=America/Sao_Paulo date '+%d/%m/%Y %H:%M:%S')"
echo " (esperado: a EC2 de PRODUCAO)"
echo "============================================================"

marcar() { printf '  [%s] %s\n' "$1" "$2"; }

# =============================================================================
echo
echo "=== FABIANO-29 — o backup pre-deploy funciona? ==="
# =============================================================================
if command -v mysqldump >/dev/null 2>&1; then
  marcar OK "mysqldump existe: $(mysqldump --version 2>&1 | head -1)"
else
  marcar XX "mysqldump NAO existe — era esta a causa raiz do card"
fi

echo
echo "  dumps pre-deploy em /app/backups/pre-deploy/ (8 mais recentes):"
sudo ls -lht /app/backups/pre-deploy/ 2>/dev/null | head -9 | sed 's/^/    /' \
  || echo "    (diretorio inacessivel ou inexistente)"

ULTIMO=$(sudo ls -1t /app/backups/pre-deploy/db_before_*.sql.gz 2>/dev/null | head -1)
if [ -n "$ULTIMO" ]; then
  BYTES=$(sudo stat -c%s "$ULTIMO")
  COMPLETO=$(sudo gunzip -c "$ULTIMO" 2>/dev/null | tail -5 | grep -c "Dump completed")
  TABELAS=$(sudo gunzip -c "$ULTIMO" 2>/dev/null | grep -c "CREATE TABLE")
  INSERTS=$(sudo gunzip -c "$ULTIMO" 2>/dev/null | grep -c "^INSERT INTO")
  echo
  echo "  ultimo dump: $(basename "$ULTIMO")"
  echo "    bytes ............ $BYTES        (um gzip vazio tem 20)"
  echo "    'Dump completed' . $COMPLETO         (esperado 1)"
  echo "    CREATE TABLE ..... $TABELAS        (esperado ~24)"
  echo "    INSERT INTO ...... $INSERTS"
  if [ "$BYTES" -gt 10240 ] && [ "$COMPLETO" -ge 1 ] && [ "$TABELAS" -ge 1 ]; then
    marcar OK "o dump pre-deploy tem conteudo real"
  else
    marcar XX "o dump pre-deploy nao passa nas proprias validacoes do deploy-safe.sh"
  fi
else
  marcar XX "nenhum db_before_*.sql.gz encontrado"
fi

# =============================================================================
echo
echo "=== FABIANO-30 — checklist de pendencias manuais ==="
# =============================================================================

echo
echo "  1) os quatro scripts em /app:"
for f in backup-db.sh renovar-certificados.sh enviar-backup-email.py deploy-safe.sh; do
  if sudo test -f "/app/$f"; then
    printf '     %-28s %s\n' "$f" "$(sudo stat -c '%A %U:%G %y' "/app/$f" | cut -d. -f1)"
  else
    printf '     %-28s AUSENTE\n' "$f"
  fi
done

echo
echo "  2) crontab do root — linhas do backup:"
LINHAS=$(sudo crontab -l 2>/dev/null | grep -c 'backup-db.sh')
sudo crontab -l 2>/dev/null | grep -n 'backup-db.sh' | sed 's/^/     /' || echo "     (nenhuma)"
if [ "$LINHAS" = "1" ]; then
  marcar OK "exatamente UMA linha do backup-db.sh"
else
  marcar XX "$LINHAS linha(s) do backup-db.sh — o criterio pede exatamente uma"
fi

echo
echo "  3) /etc/fabiano-backup.env:"
if sudo test -f /etc/fabiano-backup.env; then
  PERM=$(sudo stat -c '%a %U:%G' /etc/fabiano-backup.env)
  echo "     permissao: $PERM"
  case "$PERM" in
    600\ root:root) marcar OK "600 root:root" ;;
    *)              marcar XX "esperado '600 root:root'" ;;
  esac
  echo "     variaveis definidas (SO OS NOMES, nunca os valores):"
  sudo grep -oE '^[A-Z_]+=' /etc/fabiano-backup.env 2>/dev/null | tr -d '=' | sed 's/^/       /'
  echo "     MAIL_TO = $(sudo grep -E '^MAIL_TO=' /etc/fabiano-backup.env | cut -d= -f2-)"
else
  marcar XX "/etc/fabiano-backup.env NAO existe"
fi

echo
echo "  4) o cron rodou sozinho? (log do backup diario)"
sudo tail -20 /var/log/backup-db.log 2>/dev/null | sed 's/^/     /' \
  || echo "     (sem /var/log/backup-db.log)"

echo
echo "  5) backups diarios no disco:"
sudo ls -lht /app/backups/diario/ 2>/dev/null | head -6 | sed 's/^/     /' \
  || echo "     (sem /app/backups/diario/)"

echo
echo "============================================================"
echo " Nada foi alterado nesta maquina."
echo "============================================================"
