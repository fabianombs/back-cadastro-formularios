# RUNBOOK — Projeto Fabiano (backend)

> Documento para ser aberto às 23h de um sábado, quando ninguém lembra de
> comando nenhum. Tudo aqui é para copiar e colar. Onde houver valor a
> preencher, a mesma linha diz onde encontrá-lo.

**Atualizado em:** 03/08/2026

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

    sudo crontab -l | grep backup           # deve ter a linha das 06:30 UTC (03:30 BRT)
    ls -lht /app/backups/diario/ | head
    tail -50 /var/log/backup-db.log
    sudo /app/backup-db.sh                  # rodar na hora

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

## 8. Contatos e janelas

| Item | Valor |
|---|---|
| Responsável técnico | Vini |
| Cliente e dono dos dados | Fabiano |
| Janela de manutenção | a combinar com o Fabiano |
| Canal de alerta | a definir — FABIANO-28 |
| Backup por e-mail | a configurar — falta o `/etc/fabiano-backup.env` e o e-mail do Fabiano |

---

## 9. Limitações honestas deste runbook

O card pedia que cada procedimento fosse **executado ao menos uma vez** antes de
ser considerado documentado. Ainda não é o caso de todos:

| Procedimento | Já executado? |
|---|---|
| Deploy normal | sim, muitas vezes |
| Leitura de logs e diagnóstico | sim, em 02 e 03/08 |
| Backup manual validado | **sim**, 03/08 — 24 tabelas, 22 inserts |
| Instalação do cliente MySQL | **sim**, 03/08, com ensaio em container antes |
| Troca de autenticação do banco (7.7) | **sim**, 04/08 — em produção, com rollback armado |
| Rollback manual (`rollback.sh`) | **não** |
| Recuperação manual (seção 5) | **não** |
| Restauração de dump (6.2) | **não** — FABIANO-20 |
| Point-in-time recovery (6.1) | **não** — depende do FABIANO-4 |

Procedimento não executado é hipótese. Os das seções 4, 5 e 6 ainda são hipótese,
e é justamente neles que se confia num sábado à noite.

**A prioridade seguinte é executar o teste de restauração (FABIANO-20)** e voltar
aqui para preencher o RTO real.
