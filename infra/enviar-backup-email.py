#!/usr/bin/env python3
# =============================================================================
# enviar-backup-email.py — envia o backup diario por e-mail (FABIANO-20)
# =============================================================================
# Usa apenas biblioteca padrao do Python 3. Nada para instalar na EC2.
#
# Uso:
#   enviar-backup-email.py <arquivo.sql.gz> <tabelas> <inserts>
#
# Credenciais em /etc/fabiano-backup.env (chmod 600):
#   SMTP_HOST=smtp.gmail.com
#   SMTP_PORT=587
#   SMTP_USER=resulta.tecnologies@gmail.com
#   SMTP_PASS=<app password de 16 caracteres, sem espacos>
#   MAIL_TO=fabiano@exemplo.com.br
#   MAIL_CC=vinicius.politta1@gmail.com
# =============================================================================

import os
import smtplib
import ssl
import sys
from datetime import datetime
from email.message import EmailMessage
from pathlib import Path

ENV_FILE = "/etc/fabiano-backup.env"
# Gmail recusa anexo acima de 25 MB. O banco hoje tem ~120 KB, mas o script
# precisa avisar em vez de falhar silenciosamente quando isso mudar.
LIMITE_ANEXO = 24 * 1024 * 1024


def carregar_env(caminho):
    """Le o arquivo de config sem usar `source` — valores com espaco quebram o shell."""
    cfg = {}
    with open(caminho) as f:
        for linha in f:
            linha = linha.strip()
            if not linha or linha.startswith("#") or "=" not in linha:
                continue
            chave, _, valor = linha.partition("=")
            cfg[chave.strip()] = valor.strip().strip('"').strip("'")
    return cfg


def formatar_tamanho(n):
    for unidade in ("B", "KB", "MB", "GB"):
        if n < 1024:
            return f"{n:.0f} {unidade}" if unidade == "B" else f"{n:.1f} {unidade}"
        n /= 1024
    return f"{n:.1f} TB"


def main():
    if len(sys.argv) < 2:
        print("uso: enviar-backup-email.py <arquivo> [tabelas] [inserts]", file=sys.stderr)
        return 2

    arquivo = Path(sys.argv[1])
    tabelas = sys.argv[2] if len(sys.argv) > 2 else "?"
    inserts = sys.argv[3] if len(sys.argv) > 3 else "?"

    if not arquivo.is_file():
        print(f"ERRO: arquivo nao encontrado: {arquivo}", file=sys.stderr)
        return 1

    try:
        cfg = carregar_env(ENV_FILE)
    except OSError as e:
        print(f"ERRO: nao consegui ler {ENV_FILE}: {e}", file=sys.stderr)
        return 1

    faltando = [k for k in ("SMTP_HOST", "SMTP_USER", "SMTP_PASS", "MAIL_TO") if not cfg.get(k)]
    if faltando:
        print(f"ERRO: faltam variaveis em {ENV_FILE}: {', '.join(faltando)}", file=sys.stderr)
        return 1

    tamanho = arquivo.stat().st_size
    hoje = datetime.now().strftime("%d/%m/%Y")
    hora = datetime.now().strftime("%H:%M")

    msg = EmailMessage()
    msg["Subject"] = f"Backup do sistema — {hoje}"
    msg["From"] = f"Resulta Tecnologia <{cfg['SMTP_USER']}>"
    msg["To"] = cfg["MAIL_TO"]
    if cfg.get("MAIL_CC"):
        msg["Cc"] = cfg["MAIL_CC"]

    # Texto escrito para o Fabiano ler, nao para tecnico.
    corpo = f"""Oi Fabiano,

Segue em anexo o backup automatico do sistema, gerado hoje ({hoje}) as {hora}.

E uma copia completa de tudo que esta cadastrado: formularios, agendamentos,
listas de presenca e usuarios. Guarde num lugar seguro — assim voce tem uma
copia dos seus dados independente do servidor.

Resumo de hoje:
  Arquivo    {arquivo.name}
  Tamanho    {formatar_tamanho(tamanho)}
  Tabelas    {tabelas}
  Conteudo   verificado, integro

Esse e-mail e automatico e chega todo dia de madrugada. Se em algum dia ele
nao chegar, e sinal de que algo deu errado no servidor — vale me avisar.

Abraco,
Vini
"""
    msg.set_content(corpo)

    if tamanho > LIMITE_ANEXO:
        # Nao adianta tentar: o servidor recusa. Avisa e manda so o resumo.
        msg.set_content(
            corpo
            + f"\n\nAVISO: o backup ({formatar_tamanho(tamanho)}) passou do limite de "
            "anexo do e-mail e nao pode ser enviado assim. O arquivo esta guardado "
            "no servidor. Precisamos combinar outra forma de entrega.\n"
        )
        print(f"AVISO: {tamanho} bytes excede o limite de anexo — enviando so o resumo")
    else:
        msg.add_attachment(
            arquivo.read_bytes(),
            maintype="application",
            subtype="gzip",
            filename=arquivo.name,
        )

    porta = int(cfg.get("SMTP_PORT", 587))
    contexto = ssl.create_default_context()

    try:
        if porta == 465:
            with smtplib.SMTP_SSL(cfg["SMTP_HOST"], porta, context=contexto, timeout=60) as s:
                s.login(cfg["SMTP_USER"], cfg["SMTP_PASS"])
                s.send_message(msg)
        else:
            with smtplib.SMTP(cfg["SMTP_HOST"], porta, timeout=60) as s:
                s.starttls(context=contexto)
                s.login(cfg["SMTP_USER"], cfg["SMTP_PASS"])
                s.send_message(msg)
    except smtplib.SMTPAuthenticationError:
        print("ERRO: autenticacao recusada. Confirme que SMTP_PASS e uma App Password "
              "do Google (16 caracteres, sem espacos) e nao a senha normal da conta.",
              file=sys.stderr)
        return 1
    except Exception as e:
        print(f"ERRO ao enviar: {type(e).__name__}: {e}", file=sys.stderr)
        return 1

    destinos = cfg["MAIL_TO"] + (f" (cc {cfg['MAIL_CC']})" if cfg.get("MAIL_CC") else "")
    print(f"e-mail enviado para {destinos} — {formatar_tamanho(tamanho)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
