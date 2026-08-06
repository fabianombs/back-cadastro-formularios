# RUNBOOK — Projeto Fabiano (backend)

> Documento para ser aberto às 23h de um sábado, quando ninguém lembra de
> comando nenhum. Tudo aqui é para copiar e colar. Onde houver valor a
> preencher, a mesma linha diz onde encontrá-lo.

**Atualizado em:** 06/08/2026

---

## 0. ONDE EU ESTOU? (migracao blue-green em andamento)

> [!danger] Existem DUAS maquinas agora. Confira antes de digitar qualquer coisa.

```bash
# PRODUCAO — atende o cliente hoje. Elastic IP, Amazon Linux 2, JAR no systemd.
ssh -i ~/.ssh/poc-fabiano ec2-user@100.30.35.83

# NOVA — HOMOLOGACAO desde 05/08. Atende api-hml e grafana-hml. AL2023, Docker.
ssh -i ~/.ssh/poc-fabiano ec2-user@54.197.175.159
```

No PowerShell, trocar `~` por `$HOME`:

    ssh -i $HOME\.ssh\poc-fabiano ec2-user@100.30.35.83
    ssh -i $HOME\.ssh\poc-fabiano ec2-user@54.197.175.159

> [!warning] O IP da maquina nova mudou em 06/08
> Era `44.193.5.38`, um IP **efemero** — parar a instancia o devolvia para a AWS.
> Ao trocar o tipo para t3.medium foi preciso desligar, entao alocamos um Elastic
> IP proprio: **`54.197.175.159`** (`eipalloc-053acd67132fed0af`). Agora ele
> pertence a maquina e sobrevive a stop/start.
>
> Quem tambem dependia do endereco antigo, e foi corrigido junto:
> o registro A de `api-hml` na Vercel, o secret `HOMOLOG_EC2_HOST` do GitHub
> (a esteira falhava com `dial tcp ***:22: i/o timeout`), e o certificado de
> `44-193-5-38.sslip.io`, que foi **apagado** — o nome derivava do IP efemero.

### Como saber em qual voce esta

| | PRODUCAO (antiga) | NOVA (em construcao) |
|---|---|---|
| IP publico | `100.30.35.83` (Elastic IP) | `54.197.175.159` (Elastic IP desde 06/08) |
| Prompt | `ec2-user@ip-172-31-28-215` | `ec2-user@ip-172-31-12-104` |
| Instancia | `i-0987e63c336e202b9` | `i-008f8d272588845ef` |
| Tipo / AZ | `t2.micro` / `us-east-1c` | `t3.medium` (4 GB, desde 06/08) / `us-east-1a` |
| SO | Amazon Linux 2 (**sem suporte** desde 30/06/2026) | Amazon Linux 2023 |
| Runtime | JAR no systemd | Docker + Compose |
| Banco | `poc-fabiano-db` — MySQL 8.0.45 | `poc-fabiano-db-ensaio` — MySQL **8.4.10** (desde 05/08) |
| Observabilidade | nao tem | Prometheus, Loki, Promtail, Grafana, node-exporter (secao 8) |
| Marca infalivel | `/etc/poc-fabiano.env` **existe** | esse arquivo **nao existe** |

> [!tip] A marca infalivel e a que vale
> IP se digita errado e prompt se confunde. `[ -f /etc/poc-fabiano.env ]` nao.
> Todo script perigoso deste runbook comeca com essa checagem — em produtos
> diferentes, no sentido certo.

> [!warning] Enquanto as duas existirem
> A **antiga** e quem atende o cliente. A **nova** deixou de estar "em
> construcao" em 05/08: ela atende `api-hml.nexventa.com.br` e
> `grafana-hml.nexventa.com.br`, e o frontend de homologacao
> (`hml.nexventa.com.br`, Preview da Vercel atrelado a branch `develop`) fala
> com ela. O banco dela e o de **ensaio**, nunca o de producao.
>
> A nova **tem** cron de backup ativo (o AL2023 nao traz cron; foi preciso
> `dnf install -y cronie`). Enquanto as duas rodarem backup, chegam dois e-mails
> por dia — e isso e esperado, nao defeito.
>
> `certbot renew --dry-run` **falha** na maquina nova para
> `100-30-35-83.sslip.io`. Tambem esperado: esse nome resolve para a maquina
> antiga. Nao silencie; a falha some sozinha na virada.

A virada e uma reassociacao de Elastic IP, de segundos. O dominio
`100-30-35-83.sslip.io` deriva do proprio IP, entao o certificado atravessa a
troca sem ajuste. Rollback e o mesmo comando ao contrario. Detalhes no
FABIANO-47.

---

## 1. Mapa do ambiente

| Item | Valor | Onde confirmar |
|---|---|---|
| EC2 | `100.30.35.83` (Elastic IP), Amazon Linux 2 | `ssh -i ~/.ssh/poc-fabiano ec2-user@100.30.35.83` |
| Serviço | `poc-fabiano.service` (systemd), porta 8080 | `systemctl status poc-fabiano` |
| Aplicação | `/app/app.jar`, Java 21, roda como `appuser` | — |
| Banco | RDS MySQL 8.0.45 → 8.4 | `sudo grep DB_HOST /etc/poc-fabiano.env` |
| Configuração | `/etc/poc-fabiano.env` (chmod 600) | única fonte de credenciais |
| Versões antigas | `/app/releases/` — 8 últimos jars e dumps | `ls -lht /app/releases/` |
| Versão no ar | `/app/CURRENT_VERSION` | `cat /app/CURRENT_VERSION` |
| Backups diários | `/app/backups/{diario,semanal,mensal}` | existe a partir do deploy que instala o cron |
| Proxy | nginx + Let's Encrypt, `100-30-35-83.sslip.io` | `sudo nginx -t` |
| Frontend | `nexventa.com.br` (Vercel) | — |
| Secrets do CI | GitHub → Settings → Secrets: `EC2_HOST`, `EC2_USER`, `SSH_PRIVATE_KEY` | — |
| Secrets do CI (homolog) | `HOMOLOG_EC2_HOST`, `HOMOLOG_EC2_USER`, `HOMOLOG_SSH_PRIVATE_KEY` | — |
| Conta AWS | `135133927228`, regiao `us-east-1` | `aws sts get-caller-identity` |

### Nomes DNS e certificados

O DNS e gerenciado **na propria Vercel** — `vercel.com/tablet-s-house/~/domains/nexventa.com.br`,
secao *DNS Records*. O dominio e registrado em terceiro, mas os nameservers sao
da Vercel: **registro** se edita la; **trocar de nameserver** exige o registrador.

| Nome | Aponta para | Certificado |
|---|---|---|
| `nexventa.com.br` / `www` | Vercel (producao) | Vercel, automatico |
| `hml.nexventa.com.br` | Vercel, Preview da branch `develop` | Vercel, automatico |
| `api-hml.nexventa.com.br` | `54.197.175.159` | Let's Encrypt, expira **03/11/2026** |
| `grafana-hml.nexventa.com.br` | `54.197.175.159` | Let's Encrypt, expira **03/11/2026** |
| `100-30-35-83.sslip.io` | `100.30.35.83` | Let's Encrypt, expira **08/10/2026** |

Ha registros CAA na zona autorizando `letsencrypt.org`. Se alguem mexer neles, a
emissao passa a falhar com erro de CAA — sintoma nada obvio.

**Nenhuma credencial de produção está no repositório.** O `application-prod.properties`
só lê variáveis; os valores vivem no `/etc/poc-fabiano.env`.

---

## 2. Deploy normal

Disparado por **push na `master`**. Workflow: `.github/workflows/prod.yml`.

| Etapa | O que faz | Duração típica |
|---|---|---|
| `build-and-test` | compila e roda a suíte contra um MySQL 8.4 efêmero | 3 a 5 min |
| SCP | envia jar e scripts para `/home/ec2-user/deploy/` | segundos |
| `deploy-safe.sh` | backup, troca do jar, health-gate, rollback se falhar | 1 a 3 min |

Acompanhar em **GitHub → Actions**. Na EC2, ao vivo:

    sudo journalctl -u poc-fabiano -f

### As quatro etapas do deploy-safe.sh

1. Confere que `mysqldump` e `mysql` existem na máquina
2. Gera o backup e roda **cinco validações** nele
3. Troca o jar e reinicia o serviço
4. Health-gate: espera até **90 segundos** por `"status":"UP"` em `/actuator/health`

No sucesso, grava a versão em `/app/CURRENT_VERSION` e mantém os **8** jars e
dumps mais recentes em `/app/releases/`.

---

## 3. O deploy falhou. E agora?

Comece pelo **exit code**, no fim do log do passo *Deploy seguro* no GitHub Actions.

    Deploy vermelho no CI
    |
    +-- exit 2  -> BACKUP FALHOU. O deploy nem comecou.
    |              *** PRODUCAO ESTA INTACTA. Ninguem precisa correr. ***
    |              Va para 3.1
    |
    +-- exit 1  -> O jar novo subiu, nao respondeu, e o ROLLBACK JA RODOU.
    |              O sistema esta no ar na versao anterior.
    |              Va para 3.2
    |
    +-- exit 3  -> CATASTROFE: deploy falhou E o rollback tambem.
    |              O sistema pode estar fora do ar. Va para a secao 5.
    |
    +-- vermelho antes disso (build ou teste)
                   Nada saiu da maquina do CI. Corrigir e repetir.

### 3.1 — exit 2: backup falhou

A mensagem no log diz qual validação reprovou. Causas já vistas neste projeto:

| Mensagem | Causa | Correção |
|---|---|---|
| `'mysqldump' nao encontrado` | cliente MySQL ausente na EC2 | seção 7.5 |
| `mysqldump retornou 2` com `FLUSH TABLES WITH READ LOCK: Access denied (1045)` | o RDS recusa lock global até para o usuário master | falta `--set-gtid-purged=OFF` no comando |
| `arquivo com apenas 20 bytes` | gzip vazio — o dump não produziu nada | ver as duas linhas acima |
| `sem a marca 'Dump completed'` | dump interrompido no meio | rede ou timeout; repetir |
| `dump sem nenhum INSERT` | veio só a estrutura, sem dados | conferir os parâmetros do mysqldump |
| `nao consegui ler DB_HOST/DB_NAME/DB_USER` | env file ilegível ou serviço parado | `sudo cat /etc/poc-fabiano.env` |

Antes de qualquer coisa, confirme que a aplicação continua no ar:

    systemctl is-active poc-fabiano
    curl -s -o /dev/null -w "health HTTP %{http_code}\n" http://localhost:8080/actuator/health

`active` e `HTTP 200` significam que não há incidente — só um deploy bloqueado.
Pode resolver com calma.

### 3.2 — exit 1: o rollback automático já aconteceu

O sistema está no ar na versão anterior. **Não há urgência.** Confirme e investigue:

    cat /app/CURRENT_VERSION
    systemctl is-active poc-fabiano
    sudo journalctl -u poc-fabiano -n 200 --no-pager

O que procurar no log, em ordem de probabilidade: erro de migration do Flyway,
`Schema-validation` do Hibernate reclamando de coluna, falha de conexão com o
banco, variável de ambiente ausente.

Corrija, commite e faça um novo deploy. Não conserte na EC2 à mão — o próximo
deploy sobrescreveria.

---

## 4. Rollback manual

Use quando o deploy passou no health-gate mas o sistema está errado — bug de
comportamento, que o health-check não detecta.

**Listar as versões disponíveis:**

    ls -1t /app/releases/app_*.jar | sed -E 's#.*/app_(.*)\.jar#\1#'

O nome da versão é `AAAAMMDD-HHMMSS-<7 primeiros do commit>`.

**Voltar só a aplicação** — é o que se quer em quase todos os casos:

    sudo /app/rollback.sh 20260803-021500-a1b2c3d

Saída esperada: `Rollback OK -> versao <versao> no ar.`

**Voltar aplicação e banco** — leia a seção 6 antes:

    sudo /app/rollback.sh 20260803-021500-a1b2c3d --with-db

> **O `--with-db` apaga tudo que foi gravado no banco depois daquele deploy.**
> Formulário preenchido, presença marcada, agendamento feito: some. O script tira
> um dump de segurança antes, em `/app/releases/db_safety_*.sql.gz`, mas o caminho
> de volta é manual. Não use por reflexo.

---

## 5. Recuperação manual (exit 3 — o pior caso)

O rollback automático falhou e o sistema pode estar fora do ar.

**Passo 1 — ver o que está acontecendo:**

    systemctl status poc-fabiano --no-pager
    sudo journalctl -u poc-fabiano -n 100 --no-pager

**Passo 2 — descartar as causas bobas, que são as mais frequentes:**

    df -h /                      # disco cheio impede o servico de subir
    free -h                      # sem memoria e sem swap = OOM killer
    sudo dmesg | tail -20        # confirma se o kernel matou o processo

**Passo 3 — subir a última versão boa na mão:**

    ls -1t /app/releases/app_*.jar | head -3
    sudo systemctl stop poc-fabiano
    sudo cp /app/releases/app_VERSAO_BOA.jar /app/app.jar
    sudo chown appuser:appuser /app/app.jar
    sudo systemctl start poc-fabiano
    sleep 20
    curl -s http://localhost:8080/actuator/health

**Passo 4 — se nem assim subir**, o problema não é o jar. É banco, rede ou
configuração:

    DB_HOST=$(sudo grep '^DB_HOST=' /etc/poc-fabiano.env | cut -d= -f2-)
    DB_USER=$(sudo grep '^DB_USER=' /etc/poc-fabiano.env | cut -d= -f2-)
    export MYSQL_PWD=$(sudo grep '^DB_PASSWORD=' /etc/poc-fabiano.env | cut -d= -f2-)
    mysql -h "$DB_HOST" -u "$DB_USER" -N -B -e "SELECT VERSION(), CURRENT_USER();"
    unset MYSQL_PWD

Se isso falhar, o problema é o RDS ou a rede, não a aplicação.

---

## 6. Restauração de banco

> **Leia antes de executar:** toda restauração **perde dados**. A única pergunta é
> quantos. Decida o caminho pela quantidade que você aceita perder, não pela
> facilidade do comando.

### Quanto se perde em cada caminho

| Caminho | Perda | Quando usar |
|---|---|---|
| Point-in-time recovery do RDS | segundos a minutos | **preferível sempre**, se estiver habilitado |
| Dump do `backup-db.sh` | até 24h (roda 03:30) | quando o PITR não alcança |
| Dump pré-deploy `db_before_*` | desde aquele deploy | reverter estrago de um deploy específico |

**O PITR só existe se o `backup_retention_period` do RDS for maior que zero.**
Confirmar isso é o FABIANO-4 e ainda está pendente. Enquanto não for confirmado,
**assuma que não existe PITR.**

### 6.1 — Point-in-time recovery (preferível)

Console AWS, RDS, instância, **Actions → Restore to point in time**. Cria uma
instância **nova**; a antiga fica intacta. Depois de conferir os dados na nova,
aponte a aplicação para ela trocando `DB_HOST` no `/etc/poc-fabiano.env` e
reiniciando o serviço.

Não sobrescreve nada. É o caminho seguro.

### 6.2 — Restaurar de dump

    ls -lht /app/backups/diario/ | head
    ls -lht /app/releases/db_before_*.sql.gz | head

**Sempre valide o arquivo antes de restaurar.** Um dump vazio restaurado apaga o
banco sem repor nada — foi exatamente esse o risco do FABIANO-29:

    ARQ=/app/backups/diario/fabiano-AAAAMMDD-HHMMSS.sql.gz
    stat -c%s "$ARQ"                                        # esperado > 100000
    gunzip -c "$ARQ" | tail -5 | grep -c "Dump completed"   # esperado 1
    gunzip -c "$ARQ" | grep -c "CREATE TABLE"               # esperado 24
    gunzip -c "$ARQ" | grep -c "^INSERT INTO"               # esperado >= 22

Se qualquer um reprovar, **não restaure**. Procure outro arquivo.

Com o arquivo validado:

    sudo systemctl stop poc-fabiano

    DB_HOST=$(sudo grep '^DB_HOST=' /etc/poc-fabiano.env | cut -d= -f2-)
    DB_NAME=$(sudo grep '^DB_NAME=' /etc/poc-fabiano.env | cut -d= -f2-)
    DB_USER=$(sudo grep '^DB_USER=' /etc/poc-fabiano.env | cut -d= -f2-)
    export MYSQL_PWD=$(sudo grep '^DB_PASSWORD=' /etc/poc-fabiano.env | cut -d= -f2-)

    mysqldump -h "$DB_HOST" -u "$DB_USER" --single-transaction --set-gtid-purged=OFF \
      --routines --triggers "$DB_NAME" | gzip -9 > ~/antes-da-restauracao-$(date +%Y%m%d-%H%M%S).sql.gz

    gunzip -c "$ARQ" | mysql -h "$DB_HOST" -u "$DB_USER" "$DB_NAME"
    unset MYSQL_PWD

    sudo systemctl start poc-fabiano
    sleep 20
    curl -s http://localhost:8080/actuator/health

O `mysqldump` antes do `gunzip` é rede de segurança: guarda o estado atual antes
de sobrescrever.

**RTO medido:** ainda não medido. O teste de restauração é o FABIANO-20 e continua
pendente. Enquanto ele não for feito, este procedimento é teoria.

---

## 7. Comandos do dia a dia

### 7.1 — Logs

    sudo journalctl -u poc-fabiano -f                    # ao vivo
    sudo journalctl -u poc-fabiano -n 200 --no-pager     # ultimas 200
    sudo journalctl -u poc-fabiano -p err -n 50          # so erros
    sudo journalctl -u poc-fabiano --since "1 hour ago"

A partir do deploy que leva o FABIANO-26, o log sai em **JSON (ECS)**. Para ler
com conforto:

    sudo journalctl -u poc-fabiano -n 50 -o cat | python3 -m json.tool

Cada linha carrega um `requestId`. Para ver tudo de uma requisição:

    sudo journalctl -u poc-fabiano --since "30 min ago" -o cat | grep SEU_REQUEST_ID

O `requestId` chega ao usuário no header `X-Request-Id` da resposta — peça esse
valor a quem relatou o erro.

### 7.2 — Saúde

    systemctl is-active poc-fabiano
    curl -s http://localhost:8080/actuator/health
    curl -s -o /dev/null -w "%{http_code}\n" https://100-30-35-83.sslip.io/actuator/health

### 7.3 — Recursos

    df -h /
    free -h
    swapon --show                # deve mostrar 1 GB em /swapfile
    top -b -n1 | head -15

O swap foi criado em 02/08 porque o `yum` semanal derrubava a aplicação por OOM.
Se `swapon --show` vier vazio, o problema volta.

### 7.4 — Backup

    sudo crontab -l | grep backup           # 30 6 * * * = 06:30 UTC = 03:30 BRT
    ls -lht /app/backups/diario/ | head
    sudo tail -40 /var/log/backup-db.log    # backup-db.log, NAO fabiano-backup.log
    sudo /app/backup-db.sh                  # rodar na hora

Rotacao GFS: 7 diarios, 4 semanais, 12 mensais. Cinco validacoes antes de dar o
backup por concluido, incluindo contagem de `INSERT`.

**Envio por e-mail: funcionando na maquina nova.** Verificado em 05/08 — tres
envios, ~121 KB cada:

    [2026-08-05 17:29:23] e-mail enviado ao cliente
    e-mail enviado para contato@resultatec.com.br — 121.4 KB

> [!warning] `MAIL_TO` aponta para a Resulta, nao para o Fabiano
> E deliberado: fase de teste. Enquanto nao trocar, a frase "enviado ao cliente"
> no log e otimista — a copia vai para a nossa propria caixa, e o cliente
> continua dependendo exclusivamente da nossa infraestrutura. Trocar faz parte
> do checklist da virada (secao 10).

Credenciais de e-mail: `/etc/fabiano-backup.env` (root, 600) — `SMTP_HOST`,
`SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `MAIL_TO`, `MAIL_CC`.

Metrica do dead man's switch:
`/var/lib/node_exporter/textfile_collector/backup_fabiano.prom`, lida pelo
node-exporter e vigiada pelo alerta *"Backup do banco atrasado"* (26 h).

> [!note] Fuso na nomenclatura
> O nome do arquivo diz `113301` e o `ls` diz `14:33`. Nao e inconsistencia: o
> script gera o nome com `TZ=America/Sao_Paulo` e a maquina roda em UTC.

**Copia offsite em S3:** bucket `fabiano-db-backups-135133927228` criado e
privado, mas a role IAM ainda nao existe. Enquanto `FABIANO_BACKUP_BUCKET` nao
estiver no `.env`, o script pula esse trecho **em silencio** (FABIANO-20).

### 7.4.1 — Coisas que a maquina nova NAO tem

- **`git`** — arquivos chegam por CI ou `scp`, nunca por `git pull`
- **cron por padrao** — o AL2023 nao traz. `sudo dnf install -y cronie` e
  `sudo systemctl enable --now crond`. Sem isso o backup fica instalado e nunca roda
- **AWS CLI** — o caminho para comando AWS e o CloudShell

### 7.5 — Cliente MySQL (se sumir)

    sudo yum install -y https://dev.mysql.com/get/mysql84-community-release-el7-1.noarch.rpm
    sudo rpm --import https://repo.mysql.com/RPM-GPG-KEY-mysql-2023
    sudo yum install -y mysql-community-client mysql-community-libs mysql-community-libs-compat

> **Não** use `yum install mysql-community-client` sem esse repositório. No EL7
> x86_64 viria o **MySQL 9**, que removeu o `mysql_native_password` e não
> conectaria no usuário atual — o backup pararia em silêncio.
>
> O `mysql-community-libs-compat` é obrigatório: o **postfix** depende da
> `libmysqlclient.so.18`.

### 7.6 — Nginx e certificado

    sudo nginx -t
    sudo systemctl reload nginx
    sudo certbot certificates          # data de expiracao

O certificado expirou uma vez, em 10/07/2026, e derrubou o sistema. Vale conferir
a data quando estiver perto de setembro.

### 7.7 — Autenticação do usuário do banco (FABIANO-41)

O `admin@%` usa **`caching_sha2_password`** desde 04/08/2026. Antes usava
`mysql_native_password`, depreciado desde o 8.0.34.

Conferir o método em vigor:

    sudo mysql -h <DB_HOST> -u admin -p -N -B \
      -e "SELECT plugin FROM mysql.user WHERE user='admin' AND host='%';"

> **Rollback** — uma linha, e continua válida depois do upgrade para 8.4,
> porque a AWS mantém o plugin antigo ligado (`mysql_native_password`,
> `Source=system`, `IsModifiable=False`):
>
>     ALTER USER 'admin'@'%' IDENTIFIED WITH mysql_native_password BY '<A MESMA SENHA>';

**As três armadilhas deste procedimento**, todas descobertas antes de executar:

1. **O `BY` DEFINE a senha, não a confirma.** Digitar diferente da atual não
   troca o método — troca a senha, e a aplicação para. Por isso o
   procedimento lê o `DB_PASSWORD` do `/etc/poc-fabiano.env`, prova que ela
   autentica, e reusa a mesma string. **Ninguém digita senha.**

2. **O sintoma atrasa 30 minutos.** Autenticação só acontece em conexão
   **nova**; as que já estão no pool do Hikari continuam funcionando. Com o
   `maxLifetime` padrão de 30 min, um erro só aparece meia hora depois — e
   vira "o sistema caiu do nada". Reiniciar o serviço logo após a troca
   força o pool inteiro a reautenticar e antecipa a resposta.

3. **Sem sessão âncora não há rollback.** Se o método novo não funcionasse,
   a tentativa de desfazer também falharia — ela precisa autenticar, e é a
   autenticação que estaria quebrada. O procedimento abre uma conexão
   **antes** da troca e a mantém viva (`mkfifo` + `mysql` lendo do FIFO); o
   `ALTER` e o eventual rollback saem por ela.

Rede embaixo da rede, se tudo falhar ao mesmo tempo:

    aws rds modify-db-instance --db-instance-identifier poc-fabiano-db \
      --master-user-password '<nova>' --apply-immediately

Redefine a senha do master independente de plugin. Nunca se fica trancado
para fora — mas leva minutos, contra os segundos do rollback pela âncora.

**Pré-requisito que não pode faltar:** o `caching_sha2_password` exige TLS
no primeiro handshake, ou `allowPublicKeyRetrieval=true`. Sem uma das duas,
o driver falha com `Public Key Retrieval is not allowed` — erro que ninguém
associa a mudança de autenticação. O `application-prod.properties` traz
`sslMode=REQUIRED`, e o `Ssl_cipher` foi conferido em vigor antes da troca.

---

## 8. Observabilidade (maquina nova)

Subiu em 06/08/2026. Painel: **https://grafana-hml.nexventa.com.br**, usuario `admin`.

Prometheus e Loki **nao publicam porta no host** — so existem na rede
`fabiano-internal`. O Grafana chega pela internet unicamente pelo nginx, que
termina o TLS. Os quatro paineis ficam na pasta **Fabiano**.

    cd ~/fabiano/deploy
    docker compose -f docker-compose.observability.yml ps
    docker compose -f docker-compose.observability.yml up -d
    docker compose -f docker-compose.observability.yml restart grafana
    docker compose -f docker-compose.observability.yml down     # mantem os dados

> [!danger] A ordem de subida importa — errar derruba o site
> A stack usa a rede `fabiano-internal`, **criada pelo compose principal**. E o
> `nginx.conf` tem `proxy_pass http://grafana:3000`, resolvido **na hora em que o
> nginx sobe**.
>
> ```
> 1. compose principal          (cria a rede, sobe backend e nginx)
> 2. compose de observabilidade (cria o container 'grafana')
> 3. so entao reiniciar o nginx, se a config mudou
> ```
>
> Reiniciar o nginx antes de o container `grafana` existir: o nginx **nao sobe**,
> e leva o site junto.

### Diagnostico

    # os alvos estao sendo raspados?
    docker exec fabiano-prometheus wget -qO- 'http://localhost:9090/api/v1/targets?state=active' \
    | python3 -c '
    import sys, json
    for t in json.load(sys.stdin)["data"]["activeTargets"]:
        print("{:<12} {:<8} {}".format(t["labels"]["job"], t["health"], t.get("lastError") or "-"))'

    # idade do ultimo backup, em horas
    docker exec fabiano-prometheus wget -qO- \
      'http://localhost:9090/api/v1/query?query=(time()-backup_fabiano_timestamp_seconds)/3600' \
    | python3 -c '
    import sys, json
    r = json.load(sys.stdin)["data"]["result"]
    print("%.1f horas" % float(r[0]["value"][1]) if r else ">>> SEM METRICA")'

    # o Loki esta recebendo?
    docker exec fabiano-loki wget -qO- 'http://localhost:3100/loki/api/v1/label/application/values'

### Armadilhas ja pagas

- **`permission denied` no token de metricas.** O container do Prometheus roda
  como `nobody` (uid 65534). O arquivo `deploy/observability/metrics-token`
  precisa de `sudo chown 65534:65534` e `chmod 400`. Dono errado, nao permissao errada.
- **A variavel e `METRICS_SCRAPE_TOKEN`**, nao `METRICS_TOKEN`. Ausente,
  `/actuator/prometheus` devolve **401** e o alvo fica `down`.
- **`performance_schema` esta desligado no RDS** (`@@performance_schema = 0`).
  `events_statements_summary_by_digest` aceita `TRUNCATE` e devolve tabela vazia —
  nao da para investigar consulta por digest neste ambiente.

### O que ainda nao funciona

- **O Grafana nao envia e-mail** enquanto `GF_SMTP_*` nao estiver no `.env`. O
  contact point mostra *"Last delivery attempt failed"*: o alerta acende na tela
  e ninguem e avisado.
- **Se a EC2 morrer, o Grafana morre junto** e nenhum alerta sai. O monitoramento
  vigia tudo, menos a propria morte. Desenhos possiveis: monitoramento mutuo
  entre prod e homolog depois da virada, ou vigia externo (alarme CloudWatch
  `StatusCheckFailed` + monitor HTTP de fora).

---

## 9. Checklist da virada (FABIANO-47)

Itens faceis de esquecer, quase todos no `.env` da maquina nova:

- [ ] `APP_FRONTEND_URL` -> `https://www.nexventa.com.br` (hoje aponta para `hml`)
- [ ] `MAIL_TO` -> e-mail do Fabiano (hoje `contato@resultatec.com.br`)
- [ ] **Remover `https://*.vercel.app`** do `CORS_ALLOWED_ORIGINS` — o curinga e so de ensaio
- [ ] `GRAFANA_ROOT_URL` -> `https://grafana.nexventa.com.br`
- [ ] `ALERTA_SUBMISSAO_PAUSADO` -> `false`
- [ ] `DB_HOST` -> `poc-fabiano-db`; a partir dai a trava do `develop.yml` barra
      esta maquina, de proposito
- [ ] Criar `api.nexventa.com.br` e `grafana.nexventa.com.br` apontando para o Elastic IP
- [ ] Emitir os certificados dos dois **antes** de o nginx.conf com os blocos deles chegar —
      bloco `ssl_certificate` apontando para arquivo inexistente derruba o nginx inteiro
- [ ] Remover o bloco `100-30-35-83.sslip.io` so **depois** que o EIP migrar
- [ ] Upgrade do RDS de producao para 8.4 — ensaiado em 05/08: 2 min 41 s, e a
      aplicacao se recuperou **sozinha**, sem restart (o HikariCP reconstruiu o pool)

---

## 10. ZONA DE PERIGO

### `terraform apply` hoje quebraria producao

O `infra/terraform/main.tf` **nao descreve a realidade**:

```hcl
instance_type           = "t2.micro"          # a maquina real e t3.medium
ami                     = "ami-0c02fb..."     # Amazon Linux 2; a real e AL2023
backup_retention_period = 0                   # a real tem 7 dias
skip_final_snapshot     = true                # a real tem deletion protection
```

E o `infra/terraform/user_data.sh` ainda instala **Java 21 e um servico systemd** —
a arquitetura de antes do Docker.

> [!danger] Nao rode `terraform apply` ate isso ser corrigido (FABIANO-10)
> Ele devolveria a maquina errada, do tipo errado, com o sistema errado — e
> **desligaria o backup automatico do banco de producao** no caminho. O plano de
> recuperacao esta pior que nao ter plano, porque parece que existe.

### Antes de qualquer comando destrutivo na AWS

Os identificadores sao parecidos e um deles e producao:

    poc-fabiano-db-ensaio    <- ensaio, pode
    poc-fabiano-db           <- PRODUCAO

Upgrade de versao maior **nao tem rollback**.

---

## 11. Contatos e janelas

| Item | Valor |
|---|---|
| Responsável técnico | Vini |
| Cliente e dono dos dados | Fabiano |
| Conta AWS | `135133927228`, `us-east-1` |
| Usuário AWS em uso | `contato@resultatec.com.br` — **sem permissão de IAM** |
| Janela de manutenção | a combinar com o Fabiano |
| Canal de alerta | Grafana, só na tela — e-mail pendente |
| Backup por e-mail | funcionando, indo para a Resulta (fase de teste) |

---

## 12. Limitações honestas deste runbook

O card pedia que cada procedimento fosse **executado ao menos uma vez** antes de
ser considerado documentado. Ainda não é o caso de todos:

| Procedimento | Já executado? |
|---|---|
| Deploy normal | sim, muitas vezes |
| Leitura de logs e diagnóstico | sim, em 02 e 03/08 |
| Backup manual validado | **sim**, 05/08 — 25 tabelas, `Dump completed`, e-mail entregue |
| Instalação do cliente MySQL | **sim**, 03/08, com ensaio em container antes |
| Troca de autenticação do banco (7.7) | **sim**, 04/08 — em produção, com rollback armado |
| Rollback manual (`rollback.sh`) | **sim**, 05/08 — os dois caminhos, na EC2 nova |
| Rollback provado por **comportamento** | **sim**, 05/08 — ver 12.4 |
| Upgrade de MySQL 8.0 → 8.4 | **sim**, 05/08, no ensaio — 2 min 41 s |
| Troca de tipo de instância com Elastic IP | **sim**, 06/08 — t3.small → t3.medium |
| Subida da stack de observabilidade | **sim**, 06/08 |
| Caminho de **falha** do `deploy-safe.sh` (rollback automático) | **não** — exige branch descartável com defeito de boot |
| Recuperação manual (seção 5) | **não** |
| Restauração de dump (6.2) | **não** |
| Point-in-time recovery (6.1) | **não** — mas o PITR **existe**, confirmado 05/08 (7 dias) |
| Envio de alerta por e-mail | **não** — SMTP do Grafana não configurado |
| Recriação da máquina do zero | **não** — e hoje a Terraform impede (seção 10) |

Procedimento não executado é hipótese. As seções 5 e 6, o caminho de falha do
deploy e a recriação da máquina ainda são hipótese — e é justamente neles que se
confia num sábado à noite.

### 12.4 — A armadilha que quase transformou o teste de rollback em teatro

Em 05/08, quatro tags diferentes apontavam para o **mesmo IMAGE ID**:

    TAG        IMAGE ID
    080c93f    41f32380b4a5
    6647635    41f32380b4a5
    8b643f2    41f32380b4a5
    previous   41f32380b4a5

Os commits entre elas mexeram só em `nginx.conf`, `.env.example`, `smoke-local.ps1`
e workflows — nada disso entra no contexto do build. Camadas idênticas, o Docker
deduplica para um ID só. **Um rollback ali passaria verde e não provaria nada.**

O teste só virou observável depois de uma correção que mexia em `src/`
(o `@CreationTimestamp` do FABIANO-53), gerando imagem de fato diferente. Aí deu
para medir **pelo comportamento**:

    15  TESTE-53-DEPOIS    2026-08-05 16:27:44   <- imagem d04160c
    16  TESTE-53-ROLLBACK  NULL                  <- imagem previous
    17  TESTE-53-FORWARD   2026-08-05 16:31:26   <- imagem d04160c

Mesma tela, mesmo banco, mesmo clique. A única variável foi a imagem.

> [!tip] Regra que ficou
> Antes de confiar num teste de rollback:
> ```bash
> docker images ghcr.io/<owner>/fabiano-back --format 'table {{.Tag}}\t{{.ID}}'
> ```
> IDs iguais = o teste não vai provar nada.
>
> Generalizando: **teste verde não prova nada se a diferença que ele deveria
> medir não existe.**

### 9.1 — Como o `rollback.sh` foi testado (05/08/2026)

Executado na EC2 nova (FABIANO-13), contra o banco de ensaio. Produção não foi
tocada.

Um problema atrapalhava o teste: a máquina só tinha **uma** imagem, então não
havia versão anterior para voltar. Foi fabricada uma segunda a partir da que
rodava, mudando só um `LABEL` — mesmo JAR, mesmo comportamento, **ID de imagem
diferente**. Isso permite provar que o container trocou de imagem de verdade,
sem introduzir código diferente na equação.

**Caminho de falha** (`./scripts/rollback.sh tag-que-nao-existe-999`): o pull
falhou com `manifest unknown`, o script saiu com 1 e o `.Id` do container era
byte a byte o mesmo de antes. A frase *"NADA foi alterado"* que ele imprime é
verdadeira — conferida, não presumida.

**Caminho de sucesso** (`./scripts/rollback.sh teste-rollback`): **32 segundos**
do comando ao `{"status":"UP"}`. Depois dele: o `LABEL` do container era o da
imagem alvo, o `.env` já dizia `BACKEND_TAG=teste-rollback` (sem isso um reboot
traria a versão ruim de volta) e o HTTPS através do nginx respondeu **200** —
prova de que o `nginx -s reload` pegou o IP novo do container, que muda a cada
recriação.

> [!tip] O `--pull never` é o detalhe que faz a rede de segurança existir
> O `docker-compose.yml` declara `pull_policy: always`. Sem esse flag, voltar
> para a tag `:previous` — que só existe **localmente**, e é justamente a saída
> para quando o GHCR está fora do ar — faria o compose tentar buscá-la no
> registry e falhar exatamente no momento do resgate.

### 9.3 — Smoke contra a máquina nova (05/08/2026)

```powershell
cd C:\projetos\Fabiano\back-cadastro-formularios
.\infra\smoke-local.ps1 -Api "https://api-hml.nexventa.com.br"
```

> [!warning] O `-Api` mudou
> O smoke era rodado contra `https://44-193-5-38.sslip.io`. Esse nome **não
> existe mais** — foi apagado em 06/08 junto com o IP efêmero. Use
> `api-hml.nexventa.com.br`, que não deriva de IP e sobrevive à virada.

**39 ok, 0 falhas, 17 pulados.** Não precisa de administrador e não altera nada
na máquina de quem roda.

O bloco `server` de `44-193-5-38.sslip.io` **já foi removido** do
`deploy/nginx/nginx.conf` e o certificado apagado com
`certbot delete --cert-name 44-193-5-38.sslip.io`, em 06/08 — no dia em que o IP
efêmero voltou para a AWS. O passo do `certbot delete` não é cosmético: sem ele,
`certbot renew` passaria a falhar todo dia tentando validar um domínio que não
aponta mais para cá, e a falha do certificado de **produção** sumiria no ruído.

> [!danger] Não aponte o domínio de produção para a máquina nova pelo `hosts`
> Foi a primeira tentativa, e custou caro: o Defender protege o `hosts`, o
> `Set-Content` **truncou o arquivo e só então falhou ao escrever**, e o
> `finally` imprimiu "restaurado" com o arquivo zerado. Um teste de leitura não
> teria percebido — o que denunciou foi conferir a resolução depois.

**Os 17 pulados não são dívida escondida.** São verificações que exigem falar
com a aplicação direto na 8080: o nginx devolve 404 para todo `/actuator` menos
`/health`, e o log em JSON fica dentro do container. Elas aparecem como
`PULADO` na tela e no resumo, e continuam rodando no smoke local. Em troca, o
modo remoto ganhou duas que só fazem sentido de fora: `/actuator/prometheus` e
`/actuator/env` **têm** que responder 404. Se virarem 401, o bloqueio caiu.

> [!warning] `docker compose up -d nginx` puxa a imagem do backend
> O `depends_on` faz o compose resolver o backend também, e `pull_policy:
> always` faz ele tentar o GHCR. Com o `docker login` expirado, o comando
> aborta inteiro e **o nginx não é recriado** — enquanto o nginx antigo segue
> servindo, o que faz parecer que deu certo. Use sempre:
>
> ```bash
> docker compose up -d --no-deps --force-recreate nginx
> ```

### 9.2 — O backup na máquina nova (05/08/2026)

Rodado à mão na EC2 nova: 25 tabelas, 23 blocos de insert, 121 KB, e-mail
recebido com o anexo íntegro. Duas coisas só apareceram por ter rodado:

**O script apontava para a máquina errada.** `ENV_FILE` era fixo em
`/etc/poc-fabiano.env` — que é exatamente a *marca infalível da máquina antiga*
(seção 0) e não existe na nova. Instalado como estava, o backup teria começado a
falhar no dia da virada, quando ninguém está olhando para ele. Agora procura em
ordem: `/etc/poc-fabiano.env`, depois o `.env` do compose.

**O log estava em UTC e o resto em Brasília.** O e-mail dizia 09:15 e o log
12:15, para o mesmo backup.

> [!warning] O `MAIL_TO` só aponta para o Fabiano depois da virada
> Enquanto a máquina nova fala com o banco de ensaio, o anexo é dado de teste
> com o assunto "Backup do sistema". Mandar isso para o cliente é pior do que
> não mandar nada: ele guardaria uma cópia que parece o negócio dele e não é.

> [!tip] Como saber qual banco um dump salvou
> `gunzip -c <dump> | grep -c _ENSAIO_NAO_E_PRODUCAO`. Zero é produção. Um dump
> de ensaio e um de produção são indistinguíveis por tamanho, número de tabelas
> e pela marca "Dump completed".

O script também passou a registrar o alvo (`alvo: banco X em Y`) no log, pelo
mesmo motivo.

**Não testado ainda:** o `--com-banco`. É a única operação do projeto que
destrói dado de forma irreversível, e testá-la de verdade exige um banco
descartável com dado conhecido — vai junto com o FABIANO-20.

Ao final, `BACKEND_TAG` voltou para `6647635` e a imagem fabricada foi removida.
A máquina ficou no mesmo estado de antes do teste.

**A prioridade seguinte é executar o teste de restauração (FABIANO-20)** e voltar
aqui para preencher o RTO real.
