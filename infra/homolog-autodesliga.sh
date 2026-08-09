#!/usr/bin/env bash
# =============================================================================
# Auto-desligamento de homolog por inatividade (FABIANO-33)
# =============================================================================
# Roda a cada 15 min por cron NA PROPRIA MAQUINA de homolog.
#
# POR QUE EXISTE
#
# Ambiente sob demanda que ninguem lembra de derrubar vira ambiente permanente
# com nome de temporario. Hoje a unica protecao e alguem lembrar — e a conta da
# AWS de agosto/2026 mostrou o que acontece quando recurso temporario e
# esquecido ligado.
#
# POR QUE NAO PRECISA DE PERMISSAO NENHUMA NA AWS
#
# A instancia e lancada com InstanceInitiatedShutdownBehavior=terminate. Entao
# um 'shutdown -h now' de dentro faz a AWS TERMINAR a instancia, nao so
# desliga-la. Sem papel IAM, sem workflow, sem chave — a maquina se mata.
#
# QUAL SINAL CONTA COMO "USO", E POR QUE
#
# NAO serve o log do nginx: o blackbox sonda https://api-hml a cada 30s de
# dentro da propria maquina, e homolog nunca ficaria ocioso.
#
# Serve o logger 'acesso' da aplicacao, que exclui /actuator de proposito
# (FABIANO-26) — ou seja, ele so registra requisicao de gente de verdade.
# Somado a sessao SSH aberta, cobre os dois jeitos de usar homolog.
#
# O QUE ELE NAO COBRE, e fica registrado
#
# Alguem olhando SO o Grafana de homolog, sem tocar na API, conta como ocioso.
# E aceitavel: quem esta so olhando painel perde uma maquina que sobe de novo
# em 15 minutos. Bloquear esse caso exigiria ler o log do nginx e distinguir a
# sonda do humano, o que troca uma regra simples por uma fragil.
# =============================================================================
set -uo pipefail

LIMITE_OCIOSO=$((24 * 3600))    # 24h sem uso
LIMITE_ABSOLUTO=$((7 * 86400))  # teto rigido: 7 dias, aconteca o que acontecer
LOG=/var/log/homolog-autodesliga.log
CONTAINER=fabiano-backend

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOG"; }

AGORA=$(date +%s)

# --- trava de seguranca ------------------------------------------------------
# Este script APAGA a maquina onde roda. Se por qualquer motivo ele acabar numa
# maquina que nao e homolog, ele precisa se recusar a agir. O .env aponta para
# o banco de homolog; produtos diferentes, bancos diferentes.
if ! grep -q '^DB_HOST=.*homolog' /home/ec2-user/fabiano/deploy/.env 2>/dev/null; then
  log "ABORTANDO: esta maquina nao parece ser homolog (DB_HOST sem 'homolog')."
  exit 1
fi

# --- alguem conectado agora? -------------------------------------------------
# Derrubar a maquina embaixo de quem esta trabalhando nela seria pior que o
# problema que este script resolve.
if who | grep -q .; then
  log "sessao SSH ativa — nada a fazer"
  exit 0
fi

# --- ha quanto tempo a maquina existe ----------------------------------------
UPTIME=$(cut -d. -f1 /proc/uptime)

if [ "$UPTIME" -gt "$LIMITE_ABSOLUTO" ]; then
  log "TETO ABSOLUTO atingido (${UPTIME}s de uptime). Terminando."
  shutdown -h now "homolog: teto de 7 dias atingido (FABIANO-33)"
  exit 0
fi

# --- ultima requisicao de gente de verdade -----------------------------------
# --since 48h limita o quanto de log e varrido: sem isso, numa maquina de dias,
# cada execucao releria o log inteiro a cada 15 minutos.
ULTIMA=$(docker logs --since 48h "$CONTAINER" 2>&1 \
  | grep '"logger":"acesso"' \
  | tail -1 \
  | sed -n 's/.*"@timestamp":"\([^"]*\)".*/\1/p')

if [ -n "$ULTIMA" ]; then
  ULTIMA_S=$(date -d "$ULTIMA" +%s 2>/dev/null || echo 0)
  OCIOSO=$((AGORA - ULTIMA_S))
  ORIGEM="ultima requisicao"
else
  # Nenhuma requisicao no periodo varrido: conta desde o boot. Homolog que sobe
  # e nunca e usado tambem precisa morrer — e esse e o caso mais provavel de
  # esquecimento.
  OCIOSO=$UPTIME
  ORIGEM="boot (nenhuma requisicao)"
fi

log "ocioso ha ${OCIOSO}s (${ORIGEM}), limite ${LIMITE_OCIOSO}s"

if [ "$OCIOSO" -gt "$LIMITE_OCIOSO" ]; then
  log "INATIVO ha mais de 24h. Terminando a instancia."
  # Aviso em qualquer terminal aberto, caso alguem esteja olhando.
  wall "homolog sem uso ha 24h — terminando (FABIANO-33). Suba de novo com subir-homolog.sh" 2>/dev/null || true
  sleep 10
  shutdown -h now "homolog: 24h sem uso (FABIANO-33)"
fi

exit 0
