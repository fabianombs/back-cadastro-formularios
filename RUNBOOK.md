# RUNBOOK — Projeto Fabiano (backend)

> Documento para ser aberto às 23h de um sábado, quando ninguém lembra de
> comando nenhum. Tudo aqui é para copiar e colar. Onde houver valor a
> preencher, a mesma linha diz onde encontrá-lo.

**Atualizado em:** 09/08/2026 — virada de maquina e upgrade do banco para MySQL 8.4

---

## 0. ONDE EU ESTOU? (virada concluida em 08/08/2026)

> [!success] A virada aconteceu. A maquina que atende o cliente e a NOVA.
> O Elastic IP de producao `100.30.35.83` foi reassociado da maquina antiga
> para a nova as 13h de 08/08/2026
> (`eipassoc-0241dd3379a562d08`). A antiga continua ligada por seguranca,
> **sem endereco publico**, ate o FABIANO-48 desliga-la.

```bash
# PRODUCAO — atende o cliente. AL2023, Docker Compose, RDS poc-fabiano-db.
ssh -i ~/.ssh/poc-fabiano ec2-user@100.30.35.83
```

No PowerShell, trocar `~` por `$HOME`:

    ssh -i $HOME\.ssh\poc-fabiano ec2-user@100.30.35.83

### O SSH parou de funcionar depois da virada. Os dois casos.

Ambos sao consequencia esperada de mover um Elastic IP. Nenhum e defeito.

**Caso 1 — `Connection timed out` no endereco de homolog.**

    ssh ... ec2-user@54.197.175.159   ->  trava e expira

Associar um segundo Elastic IP a mesma interface **desassocia o primeiro**. A
maquina nova tinha `54.197.175.159` (homolog); ao receber `100.30.35.83`
(producao), perdeu aquele. Nao existe mais nada atras de `54.197.175.159`.
Solucao: usar `100.30.35.83`. E a mesma maquina.

**Caso 2 — `REMOTE HOST IDENTIFICATION HAS CHANGED!`**

```
@@@ WARNING: REMOTE HOST IDENTIFICATION HAS CHANGED! @@@
Offending ECDSA key in C:\Users\user/.ssh/known_hosts:2
Host key for 100.30.35.83 has changed and you have requested strict checking.
```

Isso e o SSH funcionando **corretamente**: o `known_hosts` guarda a impressao
digital por *endereco*, e ate ontem `100.30.35.83` era a maquina antiga. Hoje o
mesmo endereco entrega outra maquina, com outra chave de host. Ele nao tem como
saber que a troca foi intencional — quem sabe e voce.

Correcao (PowerShell ou bash, o comando e o mesmo):

```powershell
ssh-keygen -R 100.30.35.83
ssh -i $HOME\.ssh\poc-fabiano ec2-user@100.30.35.83
```

Ele pergunta `Are you sure you want to continue connecting?`. **Antes de
responder `yes`, confira o fingerprint** — tem que ser o da maquina nova:

    SHA256:qYhE6KDOP4kK3nd0jA4hyUMUh9YZ8pyu1OEgKwpRmec   (ED25519)

Se aparecer outro, **pare**. Ai sim pode ser problema de verdade.

> [!tip] Vale para qualquer virada futura
> Toda vez que um Elastic IP mudar de maquina, todo mundo que ja usou aquele IP
> vai levar esse aviso — inclusive a esteira, se o runner guardar `known_hosts`.
> `ssh-keygen -R <ip>` e o remedio, e conferir o fingerprint e o que separa
> "eu movi o IP" de "alguem esta no meio".

### Como saber em qual voce esta

| | PRODUCAO (nova, desde 08/08) | ANTIGA (desativada) |
|---|---|---|
| IP publico | `100.30.35.83` (Elastic IP) | **nenhum** — perdeu o EIP na virada |
| Prompt | `ec2-user@ip-172-31-12-104` | `ec2-user@ip-172-31-28-215` |
| Instancia | `i-008f8d272588845ef` | `i-0987e63c336e202b9` |
| Tipo / AZ | `t3.medium` (4 GB) / `us-east-1a` | `t2.micro` / `us-east-1c` |
| SO | Amazon Linux 2023 | Amazon Linux 2 (**sem suporte** desde 30/06/2026) |
| Runtime | Docker + Compose | JAR no systemd |
| Banco | `poc-fabiano-db` (producao) | — |
| Observabilidade | Prometheus, Loki, Promtail, Grafana, blackbox (secao 8) | nao tem |
| Marca infalivel | `/etc/poc-fabiano.env` **nao existe** | esse arquivo **existe** |

> [!tip] A marca infalivel e a que vale
> IP se digita errado e prompt se confunde. `[ -f /etc/poc-fabiano.env ]` nao.
> Todo script perigoso deste runbook comeca com essa checagem — em produtos
> diferentes, no sentido certo.

> [!warning] A maquina antiga ainda esta ligada
> Enquanto estiver, ela continua rodando **cron de backup e certbot** contra o
> banco de producao, e mandando e-mail. Dois e-mails de backup por dia sao
> esperados neste intervalo, nao defeito. O FABIANO-48 desativa os crons e
> **para** (nao termina) a instancia — ela e o rollback ate a virada provar-se
> estavel.
>
> O `certbot` dela ja e inutil: o dominio aponta para a maquina nova desde a
> virada, entao a validacao ACME falha la de qualquer jeito.

### Como entrar em cada uma

**Maquina nova (producao):**

```bash
ssh -i ~/.ssh/poc-fabiano ec2-user@100.30.35.83
```

**Maquina antiga:** ela **perdeu o IP publico** na virada. So e alcancavel de
dentro da VPC, saltando pela nova:

```bash
ssh -i ~/.ssh/poc-fabiano -J ec2-user@100.30.35.83 ec2-user@172.31.28.215
```

> [!tip] `-J` (ProxyJump), nao `-A` (agent forwarding)
> Os dois resolvem o salto. A diferenca esta em onde a chave fica exposta:
>
> - `-A` publica o socket do agente **dentro** da maquina do meio. Quem for root
>   la consegue usar a sua chave enquanto a sessao existir.
> - `-J` abre um tunel e a chave autentica nas duas pontas **direto do seu PC**.
>   A maquina do meio nunca ve credencial nenhuma.
>
> O `-A` ainda depende de o `sshd` da maquina do meio permitir forwarding, o que
> nem sempre e o caso. O `-J` nao depende de nada disso.

Para nao decorar, rodar uma vez `infra/conectar.ps1` (PowerShell). Ele escreve os
atalhos no `~/.ssh/config` e a partir dai basta:

```bash
ssh fabiano-nova
ssh fabiano-antiga    # o ProxyJump ja vai embutido
```

O script e idempotente, faz backup do `config` antes de escrever e corrige
sozinho a permissao da chave — o OpenSSH do Windows recusa chave legivel por
outros usuarios, e o erro (`UNPROTECTED PRIVATE KEY FILE`) so aparece na hora de
conectar.

**Terceiro caminho, se o SSH falhar:** as duas instancias tem a politica
`AmazonSSMManagedInstanceCore` anexada, entao aceitam **SSM Session Manager** —
sem chave, sem IP publico, sem porta 22 aberta:

```bash
aws ssm start-session --target i-008f8d272588845ef --region us-east-1
```

### Rollback da virada

Um comando, segundos, sem perda de dados novos **apenas se ninguem tiver
gravado nada desde a virada** — a partir da primeira escrita, voltar significa
perder essas escritas, porque as duas maquinas escrevem em bancos diferentes.

```bash
aws ec2 associate-address \
  --allocation-id eipalloc-025082e8787508bb8 \
  --instance-id i-0987e63c336e202b9 \
  --allow-reassociation --region us-east-1
```

O dominio `100-30-35-83.sslip.io` deriva do proprio IP, entao o certificado
atravessa a troca nos dois sentidos sem ajuste. Detalhes no FABIANO-47.

### Elastic IP orfao

`eipalloc-053acd67132fed0af` (`54.197.175.159`) ficou **sem associacao** apos a
virada, e EIP parado e cobrado (~US$ 3,65/mes).

> [!danger] NAO liberar este EIP — decidido em 08/08/2026
> Ele e o endereco da futura homolog (FABIANO-33), e ja vem com o trabalho
> pronto:
>
> * `api-hml.nexventa.com.br` **ja aponta** para ele
> * `grafana-hml.nexventa.com.br` **ja aponta** para ele
> * os certificados Let's Encrypt dos dois valem ate **03/11/2026**
>
> Associar este EIP a EC2 de homolog faz DNS e HTTPS funcionarem no mesmo
> instante — sem emitir certificado, sem esperar propagacao, sem tocar na
> Vercel. `aws ec2 release-address` devolveria o endereco a AWS e jogaria fora
> os dois certificados junto.
>
> Os US$ 3,65/mes compram exatamente isso. E o item mais barato desta conta.

> [!warning] Os certificados vencem em 03/11/2026
> A renovacao depende do certbot rodando **na maquina que atende aqueles
> nomes** — e ela so existira durante os ciclos de homologacao. Se nenhum ciclo
> cair na janela de renovacao (30 dias antes), eles expiram, e o ciclo seguinte
> comeca com HTTPS quebrado. Decisao pendente no FABIANO-33: `certbot renew` na
> subida, ou reemitir quando expirar.

---

## 1. Mapa do ambiente

> Descreve a maquina que atende producao **hoje** (a nova, Docker). A antiga,
> baseada em JAR + systemd, esta descrita na secao 5, que continua valendo
> enquanto ela existir.

| Item | Valor | Onde confirmar |
|---|---|---|
| EC2 | `100.30.35.83` (Elastic IP), Amazon Linux 2023 | `ssh -i ~/.ssh/poc-fabiano ec2-user@100.30.35.83` |
| Serviço | containers `fabiano-backend` e `fabiano-nginx` | `docker compose ps` em `/home/ec2-user/fabiano/deploy` |
| Aplicação | imagem `ghcr.io/...:$BACKEND_TAG`, Java 21 | `grep BACKEND_TAG ~/fabiano/deploy/.env` |
| Perfil Spring | `SPRING_PROFILES_ACTIVE=prod` | `grep SPRING_PROFILES ~/fabiano/deploy/.env` |
| Banco | RDS `poc-fabiano-db`, MySQL 8.0.45 → 8.4 | `grep DB_HOST ~/fabiano/deploy/.env` |
| Configuração | `/home/ec2-user/fabiano/deploy/.env` | única fonte de credenciais |
| Backup do `.env` | `.env.backup-pre-virada` na mesma pasta | `ls -l ~/fabiano/deploy/.env*` |
| Backups diários | `/app/backups/{diario,semanal,mensal}` | cron do `ec2-user` (`cronie`) |
| Proxy | nginx em container, Let's Encrypt, `100-30-35-83.sslip.io` | `docker exec fabiano-nginx nginx -t` |
| Frontend | `nexventa.com.br` (Vercel) | — |
| Secrets do CI | GitHub → Settings → Secrets: `EC2_HOST`, `EC2_USER`, `SSH_PRIVATE_KEY` | — |
| Secrets do CI (homolog) | `HOMOLOG_EC2_HOST`, `HOMOLOG_EC2_USER`, `HOMOLOG_SSH_PRIVATE_KEY` | — |
| Conta AWS | `135133927228`, regiao `us-east-1` | `aws sts get-caller-identity` |

> [!danger] A variavel do shell vence o `.env` — a armadilha de 08/08/2026
> O `docker-compose.yml` usa `SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}`.
> Na hora de resolver esse `${...}`, o Compose olha **primeiro a variavel do
> shell** e so depois o arquivo `.env`. O shell ganha, sempre.
>
> Foi assim que, minutos depois da virada, o backend subiu com o perfil
> **`homolog` em producao** — com o `.env` do disco dizendo `prod` o tempo todo.
> Alguem tinha feito `set -a; source .env` numa janela antes de o `.env` ser
> corrigido, e a variavel ficou pendurada naquela sessao. O `up` rodado na mesma
> janela herdou o valor velho.
>
> O sintoma e cruel porque **nao ha erro**: a aplicacao sobe, o health devolve
> 200 e o painel fica verde. A unica pista esta na primeira linha do log:
>
> ```bash
> docker logs fabiano-backend --tail 40 2>&1 | grep -i "profile is active"
> ```
>
> Antes de qualquer `docker compose up`, confira o que ele vai *mesmo* usar:
>
> ```bash
> echo "shell: [$SPRING_PROFILES_ACTIVE]"     # tem que sair vazio
> docker compose config | grep -i SPRING_PROFILES   # valor ja resolvido
> ```
>
> Na duvida sobre uma janela de terminal antiga: abra uma nova. Sai mais barato
> que descobrir isso depois.
>
> Nota tranquilizadora: o perfil errado **nao** manda a aplicacao para o banco
> errado. `DB_HOST`, `DB_NAME` e `APP_*` chegam por variavel de ambiente, que tem
> precedencia sobre qualquer `.properties`. O estrago fica nos ajustes internos
> do perfil, nao nos dados.

> [!warning] Existem DOIS arquivos `.env` nesta maquina
> O que vale e **`/home/ec2-user/fabiano/deploy/.env`** — o Compose le o `.env`
> que esta ao lado do arquivo dele. Existe tambem um
> `/home/ec2-user/fabiano/.env`, um nivel acima, que **nao e lido pelo Compose**.
> Editar o errado produz o pior sintoma possivel: o comando funciona, nao
> reclama, e nada muda. Confira sempre o caminho completo antes de salvar.

> [!danger] `sed -i` num arquivo bind-mounted congela o container — 08/08/2026
> O `nginx.conf` e montado como **arquivo unico**:
> `/home/ec2-user/fabiano/deploy/nginx/nginx.conf -> /etc/nginx/conf.d/default.conf`.
> Bind mount de arquivo prende o **inode**, nao o caminho.
>
> `sed -i` nao edita no lugar: escreve um arquivo temporario e renomeia por cima,
> criando um inode novo. No instante em que roda, o container passa a enxergar
> uma copia congelada do conteudo antigo — e toda edicao posterior (inclusive
> `cat >>`) vai para um arquivo que ele nao le.
>
> O sintoma e traicoeiro: `nginx -t` **passa** (ele testou o conteudo velho, que
> e valido) e o `reload` diz que funcionou. Foi assim que
> `https://api.nexventa.com.br` respondeu apresentando o certificado de
> `100-30-35-83.sslip.io`: o bloco novo existia no disco e nao existia para o
> nginx.
>
> Como saber, em dois comandos:
>
> ```bash
> docker exec fabiano-nginx md5sum /etc/nginx/conf.d/default.conf
> md5sum ~/fabiano/deploy/nginx/nginx.conf
> ```
>
> md5 diferente = o container esta lendo fantasma. `reload` NAO resolve; so
> recriar:
>
> ```bash
> docker compose up -d --pull never --force-recreate nginx
> ```
>
> Para editar sem cair nisso, prefira o que preserva o inode:
> `cat >>`, `python3 -c "open(...,'w')"`, ou `sed` com redirecionamento para
> arquivo temporario e depois `cat tmp > nginx.conf` (`>` trunca e reescreve o
> mesmo inode; `mv` nao).

> [!danger] Upgrade de RDS por Blue/Green? O pool fica preso no banco ANTIGO
> Descoberto em 08/08/2026, na troca para MySQL 8.4. **Nao esta na documentacao
> da AWS**, e morde qualquer aplicacao Java.
>
> No switchover, a AWS renomeia: o verde assume o nome `poc-fabiano-db` e o IP
> dele passa a ser o que o nome resolve; o azul vira `poc-fabiano-db-old1`,
> **mantem o IP antigo** e fica em somente leitura.
>
> O HikariCP reconstroi o pool nos segundos da troca — e a JVM ainda tem o IP
> antigo em cache. Resultado: as conexoes novas nascem apontando para o banco
> **abandonado**.
>
> ## Por que passa despercebido
>
> O `old1` e uma copia exata da producao de segundos atras. **Leitura funciona
> perfeitamente**: login, dashboard, listas, tudo 200, tudo com os dados certos.
> `SELECT VERSION()` pelo cliente `mysql` — processo novo, DNS novo — mostra o
> verde e a versao nova, confirmando um sucesso que a aplicacao nao esta tendo.
>
> **So a escrita denuncia**, com esta mensagem:
>
> ```
> The MySQL server is running with the --read-only option
> so it cannot execute this statement
> ```
>
> ## Como confirmar em dois comandos
>
> ```bash
> getent hosts poc-fabiano-db.cqdguyqqe6d6.us-east-1.rds.amazonaws.com
> getent hosts poc-fabiano-db-old1.cqdguyqqe6d6.us-east-1.rds.amazonaws.com
> ```
>
> Converta cada IP para hexadecimal com os bytes invertidos (172.31.14.180 ->
> `B40E1FAC`) e conte as conexoes de dentro do container — `0CEA` e a porta 3306:
>
> ```bash
> docker exec fabiano-backend cat /proc/net/tcp | grep -c "<HEX_VERDE>:0CEA"
> docker exec fabiano-backend cat /proc/net/tcp | grep -c "<HEX_OLD1>:0CEA"
> ```
>
> `ss` no host **nao serve**: as conexoes vivem no namespace de rede do
> container.
>
> ## A correcao
>
> ```bash
> docker compose up -d --pull never --force-recreate backend
> sleep 25
> docker exec fabiano-nginx nginx -s reload
> ```
>
> JVM nova, sem cache de DNS, pool nascendo no lugar certo.
>
> ## O que salvou
>
> A AWS deixa o azul em **somente leitura**. Foi isso que transformou perda
> silenciosa de dados — escritas indo para um banco que ninguem vai abrir de
> novo — num erro visivel na tela. Se o `old1` aceitasse escrita, a descoberta
> viria dias depois, procurando um cadastro que "com certeza foi feito".
>
> **Regra: depois de todo switchover de Blue/Green, recrie a aplicacao ANTES de
> declarar sucesso — e valide com uma ESCRITA, nunca com uma leitura.**

> [!danger] Recriou o backend? Recarregue o nginx — sem excecao
> O `nginx.conf` tem `proxy_pass http://backend:8080`, resolvido **uma unica vez,
> quando o nginx sobe**. Recriar o container do backend lhe da um IP novo na rede
> do Docker, e o nginx continua falando com o IP que morreu. Resultado: 502 na
> cara do usuario com **todos os paineis internos verdes** — foi o FABIANO-67.
>
> ```bash
> docker compose up -d --pull never backend
> sleep 25
> docker exec fabiano-nginx nginx -s reload
> ```
>
> O `--pull never` e obrigatorio: o GHCR nao esta autenticado nesta maquina e sem
> ele o comando falha com `denied: denied`.

> [!note] Nenhuma porta interna e publicada no host
> So o nginx expoe portas (`80` e `443`). Grafana, Prometheus, Loki e blackbox
> falam apenas pela rede interna do Docker — nao ha como alcanca-los da internet
> sem passar pelo nginx. Isso e proposital. Para abrir o Grafana sem certificado,
> use o tunel SSH da secao 8.

> [!note] Nao existe homologacao hoje — e a esteira sabe disso
> A maquina que era homolog virou producao em 08/08/2026. Ate o FABIANO-33
> recriar uma, o job `deploy-homolog` do `develop.yml` fica **pulado**, por
> `if: vars.HOMOLOG_ATIVO == 'true'` — variavel que nao existe.
>
> O que um push na `develop` faz hoje: compila, roda a suite, publica a imagem
> no GHCR, abre o PR para a `master` e **emite um aviso visivel** dizendo que
> nao houve deploy de homologacao. Nada fica vermelho, e nada fica escondido.
>
> Para religar, os tres passos juntos — esquecer um deixa a homolog surda:
>
> 1. criar a maquina (FABIANO-33)
> 2. atualizar o secret `HOMOLOG_EC2_HOST`
> 3. criar a variavel `HOMOLOG_ATIVO=true` em
>    *Settings -> Secrets and variables -> Actions -> **Variables***
>
> A trava do `-ensaio.` continua valendo e nao foi enfraquecida: se alguem
> religar a variavel apontando para producao, o deploy aborta antes de escrever.

> [!warning] `EC2_HOST` e `HOMOLOG_EC2_HOST` apontam para a mesma maquina agora
> Ate a homolog sob demanda existir (FABIANO-33), um deploy da `develop` cairia
> em cima de producao. A trava do `develop.yml` — que barra a maquina quando
> `DB_HOST` e o de producao — e o que impede isso. Nao a remova.

### Nomes DNS e certificados

O DNS e gerenciado **na propria Vercel** — `vercel.com/tablet-s-house/~/domains/nexventa.com.br`,
secao *DNS Records*. O dominio e registrado em terceiro, mas os nameservers sao
da Vercel: **registro** se edita la; **trocar de nameserver** exige o registrador.

| Nome | Aponta para | Certificado |
|---|---|---|
| `nexventa.com.br` / `www` | Vercel (producao) | Vercel, automatico |
| `hml.nexventa.com.br` | Vercel, Preview da branch `develop` | Vercel, automatico |
| `api.nexventa.com.br` | `100.30.35.83` | **a emitir** (FABIANO-47) |
| `grafana.nexventa.com.br` | `100.30.35.83` | **a emitir** (FABIANO-47) |
| `100-30-35-83.sslip.io` | `100.30.35.83` | Let's Encrypt, expira **08/10/2026** |
| `api-hml.nexventa.com.br` | `54.197.175.159` — **endereco morto** apos a virada | Let's Encrypt, expira 03/11/2026 |
| `grafana-hml.nexventa.com.br` | `54.197.175.159` — **endereco morto** apos a virada | Let's Encrypt, expira 03/11/2026 |

> [!danger] Nunca crie um registro A com o nome em branco
> Nome vazio significa o **apex** (`nexventa.com.br`), que pertence a Vercel e
> serve o frontend. Aponta-lo para a EC2 derruba o site inteiro. Aconteceu em
> 08/08/2026 e foi desfeito em minutos. Os nomes da EC2 sao `api` e `grafana`,
> sempre preenchidos.

Ha registros CAA na zona autorizando `letsencrypt.org`. Se alguem mexer neles, a
emissao passa a falhar com erro de CAA — sintoma nada obvio.

**Nenhuma credencial de produção está no repositório.** O `application-prod.properties`
só lê variáveis; os valores vivem no `.env` da máquina.

---

## 2. Deploy normal

Disparado por **push na `master`**. Workflow: `.github/workflows/prod.yml`.

| Etapa | O que faz | Duração típica |
|---|---|---|
| `Build & Test` | compila e roda a suíte contra um MySQL 8.4 efêmero | 1 a 3 min |
| `Promover imagem validada` | **não recompila nada** — marca no GHCR a mesma imagem que já passou pela homologação | segundos |
| `Publicar pacote de deploy (S3)` | envia o bundle de scripts de infra para o bucket de artefatos | segundos |
| `Deploy em producao` | SCP de `deploy/`, `observability/` e `infra/`, e roda o `deploy-safe.sh` | 1 a 3 min |

Tempo total observado em 10/08/2026: **2m37s**.

> **A tag da imagem é o SHA curto do commit da `develop`, não o do merge na `master`.**
> O passo *Promover imagem validada* existe exatamente para isso: o artefato que
> entra em produção é bit a bit o mesmo que passou pela homologação, nada é
> recompilado no meio. Em 10/08/2026 a master estava no commit `37339b8` e o que
> rodava em produção era a tag `3aacbc8` — o commit da develop. Quem procura a tag
> pelo commit da master não encontra.

Acompanhar em **GitHub → Actions**. Na EC2, ao vivo:

    docker logs -f fabiano-backend

### As cinco etapas do deploy-safe.sh

O script roda em `~/fabiano/deploy/` e recebe a tag como argumento.

1. **Backup do banco**, antes de qualquer migration, com cinco validações no dump
2. **Marca a imagem atual como `:previous`** — rede de segurança local, que funciona mesmo com o GHCR fora do ar
3. **Puxa e sobe a tag nova** (`docker compose pull` + `up -d --force-recreate`)
4. **Health-gate**: espera até **120 segundos** o container reportar `healthy`
5. **Sucesso**, ou **rollback automático** para `:previous`

O health-gate lê o `HEALTHCHECK` do container, e não um `curl` no host: o backend
**não publica porta nenhuma** — quem fala com a internet é o nginx.

No sucesso, o script grava `BACKEND_TAG=<tag>` no `~/fabiano/deploy/.env` (para
que um reboot suba a mesma versão), recarrega o nginx e mantém os **8** dumps mais
recentes em `/app/backups/pre-deploy/`.

> **Onde ver o que está rodando agora**
>
>     grep '^BACKEND_TAG=' ~/fabiano/deploy/.env
>     docker inspect --format='{{.Config.Image}}' fabiano-backend
>
> As duas têm que dizer a mesma coisa. Se divergirem, alguém subiu container à mão.

---

## 3. O deploy falhou. E agora?

Comece pelo **exit code**, no fim do log do job *Deploy em producao* no GitHub Actions.

    Deploy vermelho no CI
    |
    +-- exit 2  -> O DEPLOY NEM COMECOU (backup ou pull falhou).
    |              *** PRODUCAO ESTA INTACTA. Ninguem precisa correr. ***
    |              Va para 3.1
    |
    +-- exit 1  -> A imagem nova subiu, nao ficou saudavel, e o ROLLBACK JA RODOU.
    |              O sistema esta no ar na versao anterior.
    |              Va para 3.2
    |
    +-- exit 3  -> CATASTROFE: deploy falhou E o rollback tambem.
    |              O sistema pode estar fora do ar. Va para a secao 5.
    |
    +-- vermelho antes disso (build ou teste)
                   Nada saiu da maquina do CI. Corrigir e repetir.

### 3.1 — exit 2: o deploy nem começou

A mensagem no log diz qual guarda reprovou. Causas já vistas neste projeto:

| Mensagem | Causa | Correção |
|---|---|---|
| `'mysqldump' nao encontrado` | cliente MySQL ausente na EC2 | seção 7.5 |
| `falhou ao puxar ghcr.io/...` | tag que não existe no registry, GHCR fora do ar, ou sem login | conferir a tag (§4); `docker login ghcr.io` |
| `mysqldump retornou 2` com `FLUSH TABLES WITH READ LOCK: Access denied (1045)` | o RDS recusa lock global até para o usuário master | falta `--set-gtid-purged=OFF` no comando |
| `backup com apenas 20 bytes` | gzip vazio — o dump não produziu nada | ver as duas linhas acima |
| `sem a marca 'Dump completed'` | dump interrompido no meio | rede ou timeout; repetir |
| `backup sem nenhum CREATE TABLE` | veio só o cabeçalho | conferir os parâmetros do mysqldump |
| `DB_HOST/DB_NAME/DB_USER/DB_PASSWORD ausentes` | `.env` ilegível ou incompleto | `ls -l ~/fabiano/deploy/.env` |
| `GHCR_OWNER ausente` | idem | idem |

Antes de qualquer coisa, confirme que a aplicação continua no ar:

    docker ps --filter name=fabiano-backend --format '{{.Names}}  {{.Status}}'
    curl -s -o /dev/null -w "nginx HTTP %{http_code}\n" http://localhost/nginx-health

`healthy` e `HTTP 200` significam que **não há incidente** — só um deploy
bloqueado. Pode resolver com calma.

> [!note] Observado no ensaio de 10/08/2026
> Deploy de uma tag inexistente: o `docker compose pull` falhou com `denied`, o
> script saiu com 2 e o container em execução continuou com o **mesmo image id** de
> antes, `healthy`. O deploy morreu antes de encostar no serviço.

### 3.2 — exit 1: o rollback automático já aconteceu

O sistema está no ar na versão anterior. **Não há urgência.** Confirme:

    grep '^BACKEND_TAG=' ~/fabiano/deploy/.env      # deve dizer 'previous'
    docker ps --filter name=fabiano-backend --format '{{.Names}}  {{.Status}}'
    docker logs --tail 200 fabiano-backend

O log do deploy já traz os 50 últimos logs do container que falhou **e a análise do
banco**, que responde a única pergunta que decide se vale restaurar o dump:

    ==> ANALISE DO BANCO
        NENHUMA MIGRATION RODOU — banco intacto.
        NAO restaure o dump. O problema esta na aplicacao, nao no banco.

Se disser isso, **não restaure nada**: o problema é código, e o dump só faria
perder o que foi gravado durante o incidente. Se disser que o schema mudou, o
script lista as migrations aplicadas e mostra o SQL de cada uma, marcando as que
contêm comando destrutivo. Só nesse caso a seção 6 entra em cena.

Corrija, commite e faça um novo deploy. **Não conserte na EC2 à mão** — o próximo
deploy sobrescreve.

> **`BACKEND_TAG=previous` é um estado provisório.** A tag `:previous` é local e é
> reescrita a cada deploy. Enquanto o `.env` apontar para ela, um reboot da máquina
> sobe "a imagem anterior de agora", que não é necessariamente a que você quer.
> Assim que o incidente fechar, volte para uma tag de verdade pelo §4.

### 3.3 — exit 3

Deploy e rollback falharam. Vá direto para a seção 5.

---

## 4. Rollback manual

Use quando o deploy **passou** no health-gate e mesmo assim o sistema está errado —
bug de comportamento, que nenhum health-check detecta. O rollback automático cobre
o caso "não subiu"; este cobre "subiu e está errado".

### Onde encontrar as versões

| Preciso saber... | Onde |
|---|---|
| que versão está no ar | `grep '^BACKEND_TAG=' ~/fabiano/deploy/.env` |
| para onde dá para voltar **sem depender de rede** | `cd ~/fabiano/deploy && ./scripts/rollback.sh` (sem argumento) |
| todas as versões já publicadas | GHCR: `https://github.com/fabianombs?tab=packages` → `fabiano-back` → Versions |
| de que commit veio uma tag | a tag **é** o SHA curto do commit da develop: `github.com/fabianombs/back-cadastro-formularios/commit/<tag>` |
| o que foi para a máquina num deploy | no log do job *Deploy em producao*: `==> [3/5] Puxando e subindo a tag <tag>` |

Os dois primeiros são os que importam durante um incidente: um diz onde você está,
o outro diz para onde pode ir, e nenhum depende do GitHub estar no ar.

### Voltar só a aplicação — é o que se quer em quase todos os casos

    cd ~/fabiano/deploy
    ./scripts/rollback.sh 3aacbc8        # uma tag especifica
    ./scripts/rollback.sh previous       # a imagem imediatamente anterior

Saída esperada: `ROLLBACK OK — <tag> no ar.`

O script puxa do GHCR se a imagem não estiver na máquina, espera ficar saudável,
recarrega o nginx e grava a tag no `.env`. **Não toca no banco**, e avisa isso na
tela.

### Pelo GitHub, sem terminal

**Actions → Rollback (manual) → Run workflow.** Dois campos: a tag e uma caixa para
o banco. Funciona no navegador do celular — é o caminho quando o incidente cai no
fim de semana e a chave SSH está em casa.

> [!note] Testado em 10/08/2026
> Disparado com a tag que já estava rodando. Resposta:
> `AVISO: '3aacbc8' e a tag que ja esta rodando. Nada a fazer.` — verde em 10s, sem
> recriar container nenhum, `.env` e imagem em execução inalterados. Um rollback de
> verdade leva 30s ou mais só esperando o container ficar saudável; **a duração do
> run já diz qual dos dois aconteceu**.

### Voltar aplicação e banco — leia a seção 6 antes

    ./scripts/rollback.sh 3aacbc8 --com-banco

> **O `--com-banco` apaga tudo que foi gravado no banco depois daquele deploy.**
> Formulário preenchido, presença marcada, agendamento feito: some. O script exige
> que você digite a palavra `RESTAURAR` por extenso, e tira um dump de segurança do
> estado atual antes, em `/app/backups/pre-deploy/db_safety_*.sql.gz`. O caminho de
> volta é manual. Não use por reflexo.
>
> Pelo workflow, marcar a caixa não basta sozinho: ela vira a variável
> `CONFIRMO_PERDA_DE_DADOS=RESTAURAR`. É deliberado que a intenção precise aparecer
> duas vezes — marcar uma caixa por engano não pode ser suficiente para apagar dado
> de cliente.

---

## 5. Recuperação manual (exit 3 — o pior caso)

O rollback automático falhou e o sistema pode estar fora do ar.

**Passo 1 — ver o que está acontecendo:**

    docker ps -a --filter name=fabiano
    docker inspect --format='{{.State.Health.Status}}' fabiano-backend
    docker logs --tail 100 fabiano-backend

**Passo 2 — descartar as causas bobas, que são as mais frequentes:**

    df -h /                      # disco cheio impede o container de subir
    free -h                      # sem memoria e sem swap = OOM killer
    sudo dmesg | tail -20        # confirma se o kernel matou o processo
    docker system df             # imagens antigas acumuladas comem o disco

> O `docker image prune` do `deploy-safe.sh` só roda no ramo de **sucesso**. Uma
> sequência de deploys que falham enche o disco sem limpar nada — em 10/08/2026 a
> homologação já tinha 28 imagens acumuladas. Se o disco estiver apertado:
> `docker image prune -a --filter "until=168h"`.

**Passo 3 — subir a última versão boa na mão:**

    cd ~/fabiano/deploy
    ./scripts/rollback.sh              # lista o que existe na maquina
    ./scripts/rollback.sh <tag-boa>

Se o próprio script falhar, o caminho mais curto por baixo dele:

    cd ~/fabiano/deploy
    BACKEND_TAG=<tag-boa> docker compose up -d --no-deps --force-recreate --pull never backend
    docker compose logs -f backend

O `--pull never` é **obrigatório** quando a tag é `previous`: ela só existe
localmente, e o `docker-compose.yml` declara `pull_policy: always` — sem o flag, o
compose tentaria buscá-la no registry justo no momento do resgate.

**Passo 4 — se nem assim subir**, o problema não é a imagem. É banco, rede ou
configuração:

    cd ~/fabiano/deploy
    DB_HOST=$(grep '^DB_HOST=' .env | cut -d= -f2-)
    DB_USER=$(grep '^DB_USER=' .env | cut -d= -f2-)
    export MYSQL_PWD=$(grep '^DB_PASSWORD=' .env | cut -d= -f2-)
    mysql -h "$DB_HOST" -u "$DB_USER" -N -B -e "SELECT VERSION(), CURRENT_USER();"
    unset MYSQL_PWD

Se isso falhar, o problema é o RDS ou a rede, não a aplicação.

**Passo 5 — o backend está de pé mas o site não responde.** O nginx só sobe depois
que o backend fica saudável (`depends_on: service_healthy`), e ele resolve o IP do
container `backend` **uma vez** e guarda. Recriar o backend muda o IP; sem reload
ele fica batendo no endereço velho:

    docker ps --filter name=fabiano-nginx --format '{{.Names}}  {{.Status}}'
    docker exec fabiano-nginx nginx -t && docker exec fabiano-nginx nginx -s reload

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

> [!note] Confirmado em 09/08/2026
> `BackupRetentionPeriod = 7`, `DeletionProtection = true`, `EngineVersion = 8.4.10`.
> Conferido direto na instância nova criada pelo switchover Blue/Green — ela foi
> criada do zero pela AWS, e não era óbvio que herdaria essas duas flags do blue.
>
> **O PITR existe.** Este runbook afirmava o contrário até esta data.
>
> ```bash
> aws rds describe-db-instances --db-instance-identifier poc-fabiano-db \
>   --region us-east-1 \
>   --query 'DBInstances[0].[DeletionProtection,BackupRetentionPeriod,EngineVersion]'
> ```

### As quatro camadas, e a única que era teoria

| # | Artefato | O que é | Vale para |
|---|---|---|---|
| 1 | PITR do RDS (7 dias) | mecanismo da AWS | erro operacional recente — perde ~5 min |
| 2 | `old1-mysql80-final-20260809` | snapshot da produção real em **8.0**, no instante do switchover | **rollback de versão de engine** |
| 3 | Dump diário → S3 + e-mail | **SQL em texto**, agnóstico de engine | último recurso; restaura em 8.0 **ou** 8.4 |
| 4 | Dump pré-deploy `db_before_*` | local, na máquina | desfazer estrago de um deploy específico |

Das quatro, três são mecanismos da AWS. A camada 3 é script nosso — e foi a única
que nunca tinha sido exercitada. Deixou de ser em 09/08/2026 (ver 6.3).

### 6.1 — Point-in-time recovery (preferível)

Console AWS, RDS, instância, **Actions → Restore to point in time**. Cria uma
instância **nova**; a antiga fica intacta. Depois de conferir os dados na nova,
aponte a aplicação para ela trocando `DB_HOST` no `~/fabiano/deploy/.env` e
recriando o container: `docker compose up -d --no-deps --force-recreate backend`.

Não sobrescreve nada. É o caminho seguro.

### 6.2 — Restaurar de dump

    ls -lht /app/backups/diario/ | head
    ls -lht /app/backups/pre-deploy/db_before_*.sql.gz | head

**Sempre valide o arquivo antes de restaurar.** Um dump vazio restaurado apaga o
banco sem repor nada — foi exatamente esse o risco do FABIANO-29:

    ARQ=/app/backups/diario/fabiano-AAAAMMDD-HHMMSS.sql.gz
    stat -c%s "$ARQ"                                        # esperado > 100000
    gunzip -c "$ARQ" | tail -5 | grep -c "Dump completed"   # esperado 1
    gunzip -c "$ARQ" | grep -c "CREATE TABLE"               # esperado 24
    gunzip -c "$ARQ" | grep -c "^INSERT INTO"               # esperado >= 22

Se qualquer um reprovar, **não restaure**. Procure outro arquivo.

Com o arquivo validado:

    cd ~/fabiano/deploy
    docker compose stop backend        # o nginx passa a devolver 502 ate o fim

    DB_HOST=$(grep '^DB_HOST=' .env | cut -d= -f2-)
    DB_NAME=$(grep '^DB_NAME=' .env | cut -d= -f2-)
    DB_USER=$(grep '^DB_USER=' .env | cut -d= -f2-)
    export MYSQL_PWD=$(grep '^DB_PASSWORD=' .env | cut -d= -f2-)

    mysqldump -h "$DB_HOST" -u "$DB_USER" --single-transaction --set-gtid-purged=OFF \
      --routines --triggers "$DB_NAME" | gzip -9 > ~/antes-da-restauracao-$(date +%Y%m%d-%H%M%S).sql.gz

    gunzip -c "$ARQ" | mysql -h "$DB_HOST" -u "$DB_USER" "$DB_NAME"
    unset MYSQL_PWD

    docker compose start backend
    sleep 20
    docker inspect --format='{{.State.Health.Status}}' fabiano-backend

O `mysqldump` antes do `gunzip` é rede de segurança: guarda o estado atual antes
de sobrescrever.

### 6.3 — O ensaio de restauração (FABIANO-20)

**Executado em 09/08/2026.** Antes disso, tudo acima nesta seção era teoria.

```
 dump testado : fabiano-20260808-220349.sql.gz
 tabelas      : 24
 divergentes  : 0
 RTO medido   : 2s
```

24 de 24 tabelas com contagem idêntica à produção, incluindo `attendance_records`
(4241), `attendance_record_data` (8769) e `clients` (14 — o registro criado
**depois** do upgrade para 8.4).

**Repetir a cada trimestre.** Existe evento recorrente no calendário; o script é
versionado justamente para o ensaio não depender do histórico do terminal:

```bash
./infra/testar-restauracao.sh /app/backups/diario/fabiano-AAAAMMDD-HHMMSS.sql.gz
```

Ele sobe um MySQL 8.4 descartável em container, restaura, cronometra e compara a
contagem de cada tabela com a produção. Não escreve nada na produção — só
`SELECT COUNT(*)`.

> [!important] A EC2 não consegue baixar o próprio backup, e isso é de propósito
> A role `poc-fabiano-producao-ec2` tem `s3:PutObject` e `s3:ListBucket` no bucket
> de backup, mas **não tem `s3:GetObject`**. Os Sids da política inline `operacao`
> mostram a intenção: `EscreverBackup`, `ConferirBackup`, e o `GetObject` existe
> apontado para o bucket de **artefatos**, não o de backup.
>
> É backup *append-only*: uma EC2 comprometida sobe lixo, mas não lê nem apaga o
> que já está lá. Somado ao `NoncurrentVersionExpiration` de 90 dias do bucket,
> mesmo um upload malicioso por cima deixa o original recuperável.
>
> **Consequência operacional: quem restaura é um humano**, com credencial própria
> e auditada, baixando pela CloudShell ou pelo console. Não tente "consertar" a
> política — isso desmontaria a proteção.

**Como validar a cópia do S3 sem baixá-la.** O `ETag` de um objeto enviado em
parte única **é o MD5 do conteúdo**:

```bash
# CloudShell
aws s3api head-object --bucket fabiano-db-backups-135133927228 \
  --key diario/<ARQUIVO> --query '[ETag,ContentLength]' --output text

# na EC2
sudo md5sum /app/backups/diario/<ARQUIVO>
```

Batendo hash e tamanho, restaurar a cópia local tem a mesma validade que
restaurar a do S3.

### 6.4 — RTO real, decomposto

Os 2 segundos são o tempo de **restaurar o arquivo**, não de voltar ao ar:

| Etapa | Tempo |
|---|---|
| Baixar do S3 (124 KB) | ~1 s |
| Provisionar destino — container | ~30 s |
| Provisionar destino — RDS novo | ~15 min |
| **Restaurar o dump** | **2 s (medido)** |
| Repontar a aplicação e subir | ~1 min |

**RTO técnico: ~2 min** com container, **~20 min** com RDS. A meta do FABIANO-20
era ≤ 1 hora.

> [!tip] O número que realmente domina o RTO
> Nada disso. É o tempo até **alguém perceber e decidir restaurar**. Esse é o
> número que os alertas atacam — não o script.

---

## 7. Comandos do dia a dia

### 7.1 — Logs

    docker logs -f fabiano-backend                       # ao vivo
    docker logs --tail 200 fabiano-backend               # ultimas 200
    docker logs --since 1h fabiano-backend               # ultima hora
    docker logs fabiano-backend 2>&1 | grep -i '"log.level":"ERROR"'   # so erros

A partir do deploy que leva o FABIANO-26, o log sai em **JSON (ECS)**. Para ler
com conforto:

    docker logs --tail 50 fabiano-backend 2>&1 | python3 -m json.tool

Cada linha carrega um `requestId`. Para ver tudo de uma requisição:

    docker logs --since 30m fabiano-backend 2>&1 | grep SEU_REQUEST_ID

O `requestId` chega ao usuário no header `X-Request-Id` da resposta — peça esse
valor a quem relatou o erro.

### 7.2 — Saúde

    docker ps --filter name=fabiano --format '{{.Names}}  {{.Status}}'

    # De DENTRO do container: o backend nao publica porta no host, entao um
    # 'curl localhost:8080' na EC2 sempre falha — nao ha o que consultar de fora.
    docker exec fabiano-backend curl -fsS http://localhost:8080/actuator/health

    # Pela borda, o caminho que o cliente usa:
    curl -s -o /dev/null -w "%{http_code}\n" https://api.nexventa.com.br/actuator/health

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

    # O nginx roda EM CONTAINER desde a virada de 08/08. O nginx do host nao
    # existe mais — 'sudo systemctl reload nginx' nao recarrega nada do que
    # esta atendendo o cliente.
    docker exec fabiano-nginx nginx -t
    docker exec fabiano-nginx nginx -s reload

    # O certbot continua rodando NO HOST e escrevendo em /etc/letsencrypt, que o
    # container monta read-only. Por isso este ainda e um comando do host.
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

Subiu em 06/08/2026. Usuario `admin`. Os paineis ficam na pasta **Fabiano**.

**Nenhum servico de observabilidade publica porta no host** — Grafana,
Prometheus, Loki, blackbox e node-exporter existem so na rede
`fabiano-internal`. Quem expoe porta e apenas o nginx (`80` e `443`), que
termina o TLS. Isso e proposital: nao ha superficie de ataque direta.

### Como abrir o Grafana

| Situacao | Endereco |
|---|---|
| Ate 08/08/2026 | `https://grafana-hml.nexventa.com.br` — **morreu na virada**, o nome aponta para o EIP antigo |
| Depois do FABIANO-47 concluido | `https://grafana.nexventa.com.br` |
| **Enquanto o certificado nao existe** | tunel SSH, abaixo |

> [!tip] Tunel SSH — funciona sem certificado, sem DNS e sem abrir porta nenhuma
> Como a 3000 nao esta publicada, o tunel precisa apontar para o **IP do
> container** na rede do Docker. Na maquina, descubra-o:
>
> ```bash
> docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' fabiano-grafana
> ```
>
> Depois, numa janela nova do PowerShell (troque o IP pelo que apareceu):
>
> ```powershell
> ssh -i $HOME\.ssh\poc-fabiano -L 3000:172.18.0.5:3000 ec2-user@100.30.35.83
> ```
>
> Com essa janela aberta, `http://localhost:3000` no navegador e o Grafana da
> EC2. Fechar a janela fecha o tunel. O IP do container **muda** a cada
> recriacao — se o tunel parar de funcionar, redescubra.
>
> Detalhe: com `GRAFANA_ROOT_URL` apontando para `grafana.nexventa.com.br`,
> alguns redirecionamentos internos tentam ir para esse nome. Login e paineis
> funcionam normalmente pelo tunel; se algum botao te jogar para fora, volte
> para `http://localhost:3000` na barra de endereco.

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

## 9. A virada (FABIANO-47) — executada em 08/08/2026

Reassociacao do Elastic IP `eipalloc-025082e8787508bb8` da instancia
`i-0987e63c336e202b9` (antiga) para `i-008f8d272588845ef` (nova), as ~13h de
08/08/2026. Resultado: `eipassoc-0241dd3379a562d08`, e
`curl https://100-30-35-83.sslip.io/actuator/health` = **200**.

### O que foi feito antes (portoes)

- [x] Snapshot manual do RDS de producao, 100% disponivel antes de qualquer passo
- [x] Contagem de migrations conferida dos dois lados: prod 59, ensaio 62 —
      V60/V61/V62 analisadas uma a uma e todas seguras para rollback
      (V61 e idempotente e ja era no-op em producao)
- [x] `.env` da maquina nova apontado para `poc-fabiano-db` (producao), com
      copia de seguranca em `.env.backup-pre-virada`
- [x] `APP_FRONTEND_URL` -> `https://www.nexventa.com.br` (fecha o FABIANO-52)
- [x] Curinga `https://*.vercel.app` removido do `CORS_ALLOWED_ORIGINS`
- [x] `GRAFANA_ROOT_URL` -> `https://grafana.nexventa.com.br`
- [x] `ALERTA_SUBMISSAO_PAUSADO` -> `false`
- [x] `SPRING_PROFILES_ACTIVE=prod`
- [x] Portao 1 conferido com a nova ja no banco de producao, **antes** de mover o
      IP: health `UP`, 0 erros no log, 62 migrations, e leitura correta
      (13 clientes, 13 templates, 63 submissoes)
- [x] Registros `api` e `grafana` criados na Vercel apontando para `100.30.35.83`

### Validacao pos-virada (mesma tarde)

- [x] EIP confirmado pela propria maquina (metadata IMDSv2 devolveu `100.30.35.83`)
- [x] Zero linhas `ERROR`/`FATAL` no log do backend desde a virada
- [x] nginx recebendo trafego real e a sonda blackbox respondendo 200 a cada 30s
- [x] Login e dashboard abertos no navegador em `https://www.nexventa.com.br`,
      lendo os numeros do banco de **producao** (13 registros, 63 submissoes)
- [x] Uma escrita real pelo navegador confirmada no banco de producao — template
      criado com sucesso as 13:57 UTC, ja com o perfil `prod` ativo

> [!warning] A primeira validacao de leitura foi feita com o perfil errado
> Entre 13:29 e 13:53 UTC o backend rodou com `SPRING_PROFILES_ACTIVE=homolog`
> (ver a armadilha da variavel de shell, secao 1). A leitura conferida naquela
> janela — 13 clientes, 13 templates, 63 submissoes — apontava para o banco
> **certo**, porque `DB_HOST` vem do ambiente. Mas a validacao foi refeita depois
> do restart com `prod`, e so a segunda conta.

> [!note] Ruido esperado no log do nginx
> Varreduras automatizadas batendo em `/function.php`, `/json.php`,
> `/wp-links-opml.php` e afins aparecem o tempo todo, respondidas com 301/401.
> Isso e a internet, nao invasao. So vira assunto se algum desses devolver 2xx.

### O que ainda falta

- [ ] Emitir os certificados de `api.nexventa.com.br` e `grafana.nexventa.com.br`
      **antes** de o nginx.conf com os blocos deles chegar — bloco
      `ssl_certificate` apontando para arquivo inexistente derruba o nginx inteiro
- [ ] Remover o bloco `100-30-35-83.sslip.io` so **depois** que `api` estiver no ar
- [ ] Upgrade do RDS de producao para 8.4, em dois passos (FABIANO-9). Ensaiado em
      05/08: 2 min 41 s, e a aplicacao se recuperou **sozinha**, sem restart — o
      HikariCP reconstruiu o pool
- [ ] Desativar os crons e **parar** a maquina antiga (FABIANO-48)
- [ ] Decidir o destino do EIP orfao `eipalloc-053acd67132fed0af`

---

## 10. ZONA DE PERIGO

### `terraform apply` — resolvido em 09/08/2026, mas leia o porquê

> [!note] Este bloco dizia o contrario ate 09/08/2026
> A versao anterior afirmava que o `main.tf` declarava `t2.micro`,
> `backup_retention_period = 0` e `skip_final_snapshot = true`, e mandava **nao
> rodar `terraform apply`**. Aquilo ja tinha sido corrigido no FABIANO-10 — o
> runbook e que envelheceu. Fica registrado porque aviso de perigo desatualizado
> tem um custo proprio: ou paralisa quem confia nele, ou treina a ignorar avisos.

Estado conferido com `terraform plan` em 09/08/2026: **`0 added, 0 changed,
0 destroyed`**.

O que precisou ser corrigido naquele dia, depois da virada e do upgrade:

**1. O Elastic IP seria reassociado para a maquina antiga.** O `aws_eip.app`
declara `instance = aws_instance.app.id`, que e a maquina legada. Desde a virada
o endereco esta na maquina nova, que **nao e gerenciada por este state** de
proposito. Sem trava, qualquer `apply` — ate um que so mexesse numa tag —
proporia mover o EIP de volta, e producao cairia no mesmo segundo.

```hcl
lifecycle {
  ignore_changes  = [instance]
  prevent_destroy = true
}
```

> [!danger] `prevent_destroy` nao protegia disso
> Reassociar EIP e **update no lugar**, nao destroy. A protecao que ja existia
> cobria o cenario errado — e parecia suficiente. Vale a pergunta sempre que se
> confia num `prevent_destroy`: o estrago que eu temo e mesmo um *destroy*?

**2. Duas variaveis descrevendo a versao anterior:** `db_engine_version` estava
`8.0.45` (real: `8.4.10`) e `db_parameter_group` estava `default.mysql8.0`
(real: `poc-fabiano-mysql84`). Com aquilo, um `apply` proporia **downgrade de
versao maior** — a AWS recusa, e o erro travaria qualquer mudanca legitima.

**3. Outputs corretos que juntos mentiam:** `ec2_instance_id` vinha do recurso
(maquina antiga) e `ec2_public_ip` do Elastic IP (maquina nova). Cada um certo
isolado; lidos juntos, afirmavam que a instancia antiga atendia no IP de
producao. Renomeados para `ec2_antiga_instance_id` e `eip_producao`.

### Regra que fica: depois de toda virada, rodar `terraform plan`

Nao para aplicar — para **ler**. Uma virada muda a realidade sem tocar no codigo,
e o `plan` e o unico lugar onde essa diferenca aparece por escrito antes de virar
incidente.

```bash
cd infra/terraform && terraform plan
```

O Terraform roda na **CloudShell**, nao no Windows. O ciclo hoje e empacotar
(`Compress-Archive -Path infra\terraform\* -DestinationPath terraform4.zip`),
subir por **Actions → Upload file** e descompactar em `/tmp/tf`. A CloudShell
**nao sobrescreve** arquivo existente: apagar o antigo antes.

### As travas das esteiras — o que elas protegem, e o que nao

> [!note] Correcao de 08/08/2026
> Este runbook chegou a afirmar que o `prod.yml` faria deploy de `.jar` com
> `systemd` numa maquina Docker. **Era falso** — afirmacao feita de memoria, sem
> abrir o arquivo. O `prod.yml` usa `deploy-safe.sh` com `BACKEND_TAG` e
> `docker compose` desde o FABIANO-49. Fica registrado porque o erro custou uma
> recomendacao errada ("nao faca merge na master") por algumas horas.

As duas esteiras conferem o alvo **antes de escrever qualquer coisa nele**, pelo
`DB_HOST` do `.env` da maquina. O de ensaio traz `-ensaio.`; o de producao nao.

| Esteira | Aborta quando | Mensagem |
|---|---|---|
| `prod.yml` (master) | o `.env` **e** de ensaio | `Este job e o de PRODUCAO. Confira o segredo EC2_HOST.` |
| `develop.yml` (develop) | o `.env` **nao e** de ensaio | `Ou o HOMOLOG_EC2_HOST esta errado, ou esta maquina ja virou producao.` |

Hoje `EC2_HOST` e `HOMOLOG_EC2_HOST` apontam para a **mesma maquina**, porque a
homolog deixou de existir na virada. E justamente por isso a trava do
`develop.yml` importa: um push na `develop` bate na maquina de producao, le o
`.env`, nao encontra `-ensaio.` e **para antes de enviar arquivo**. A protecao
existe e funciona.

> [!warning] O que a trava NAO protege
> Ela olha o banco, nao a arquitetura. Se um dia a homolog voltar
> (FABIANO-33) e alguem esquecer de atualizar `HOMOLOG_EC2_HOST`, o deploy de
> homologacao continua abortando corretamente — mas ninguem e avisado de que a
> homolog nao esta recebendo nada. Falha segura, e ainda assim silenciosa.

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
| Caminho de **falha** do `deploy-safe.sh` (rollback automático) | **sim**, 10/08 — ver 12.5 |
| Caminho de **abortar antes de encostar no serviço** (exit 2) | **sim**, 10/08 — tag inexistente, container intacto |
| Rollback pelo **botão do GitHub** | **sim**, 10/08 — com a tag que já rodava, para não trocar nada |
| Recuperação manual (seção 5) | **não** |
| Restauração de dump (6.2) | **não em produção**; sim em container descartável, 09/08 (ver 6.3) |
| Point-in-time recovery (6.1) | **não** — mas o PITR **existe**, confirmado 05/08 (7 dias) |
| Envio de alerta por e-mail | **sim**, 06/08 (alerta de erro no log) e 07/08 ("Site fora do ar visto de fora", com `grafana_state_reason: NoData`) — SMTP entregando |
| Recriação da máquina do zero | **não** — e hoje a Terraform impede (seção 10) |
| Sonda externa (blackbox) pelo endereço público | **sim**, 07/08 — `probe_success=1` nos 3 domínios |
| Deploy aplicando config de observabilidade sozinho | **sim**, 07/08 — deploy #39 (FABIANO-63/68) |

Procedimento não executado é hipótese. A seção 5 e a restauração em produção da
6.2 ainda são hipótese — e é justamente nelas que se confia num sábado à noite.

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

---

### 12.5 — O runbook descreveu por dois dias um mundo que não existia mais

Em 10/08/2026, ao verificar o critério *"runbook de deploy/rollback documentado"*
do FABIANO-2, a checagem foi feita nos **títulos** das seções: existia um "§4
Rollback manual", existia um "§3.2 exit 1", logo o critério estava cumprido.

O conteúdo dessas seções ainda era o mundo do JAR com systemd, encerrado na virada
de 08/08. O runbook mandava rodar `sudo journalctl -u poc-fabiano -f`,
`ls -1t /app/releases/app_*.jar`, `sudo /app/rollback.sh ... --with-db` e
`sudo systemctl stop poc-fabiano`. Nenhum desses alvos existe: o serviço systemd
foi removido, `/app/releases/` não recebe jar desde a virada, e o
`/etc/poc-fabiano.env` que a seção 6 mandava ler é — segundo a **seção 1 deste
mesmo documento** — a marca infalível de que você está na máquina *antiga*.

A contagem no momento da descoberta: **61 menções ao mundo do JAR contra 23 ao
mundo do Docker**. As seções atualizadas na virada foram a 0, a 1 e da 8 em
diante. As 2 a 6 — exatamente as que alguém abre com o sistema fora do ar —
ficaram para trás.

Duas coisas valem ser guardadas disto:

**A primeira é sobre a verificação.** Conferir o índice não é conferir o
documento, pela mesma razão que `[ -d /etc/letsencrypt/archive ]` rodado sem
`sudo` não confere se o certificado existe: é um teste que não distingue *ausente*
de *invisível*, ou *presente* de *correto*. Um runbook desatualizado tem exatamente
a mesma aparência externa de um runbook em dia.

**A segunda é sobre o custo.** Documentação errada é pior que documentação
ausente. Quem abre um runbook vazio sabe que está sozinho e improvisa. Quem abre
este ia gastar os primeiros dez minutos de um incidente colecionando
`Unit poc-fabiano.service not found` e `No such file or directory`, achando que
tinha quebrado mais alguma coisa — no único momento em que os dez primeiros
minutos importam.

As seções 2 a 6 foram reescritas no mesmo dia, com as saídas reais dos ensaios de
10/08 no lugar das saídas imaginadas.
