---
title: "Observabilidade do back-cadastro-formularios: métricas, logs e dashboards"
tags: [fabiano, observabilidade, grafana, prometheus, loki, micrometer, promtail]
created: 2026-08-03
updated: 2026-08-03
status: em-analise
cards: [FABIANO-22, FABIANO-23, FABIANO-24, FABIANO-25, FABIANO-26, FABIANO-27, FABIANO-34, FABIANO-35, FABIANO-36, FABIANO-37]
---

# Observabilidade do back-cadastro-formularios

Fechamento de 03/08/2026. Substitui e amplia a nota de mapa de endpoints do
mesmo dia.

## O que existe hoje

Stack no `docker-compose.dev.yml`, perfil `observabilidade`: Prometheus
v2.53.0, Loki 3.1.0, Promtail 3.1.0, Grafana 11.1.0. Quatro telas provisionadas
por arquivo, geradas por script.

| Camada | Entrega |
|---|---|
| Métrica de infra | Micrometer + `/actuator/prometheus` com token (FABIANO-22) |
| Métrica de negócio | 12 famílias instrumentadas nos serviços (FABIANO-25) |
| Log estruturado | ECS JSON + `requestId` no MDC (FABIANO-26) |
| Log de acesso | uma linha por requisição, logger `acesso` (FABIANO-24) |
| Painéis | 4 telas, 55 painéis, 145 consultas (FABIANO-27) |

## As lições que valem mais que o código

### 1. Teste que passa sem examinar nada

O tema recorrente do projeto inteiro. Casos reais encontrados:

- `[ -s arquivo ]` num gzip vazio de **20 bytes** — passou por dois anos
- `mysqladmin ping` devolvendo 0 mesmo com autenticação recusada
- laço cujo glob não casava com arquivo nenhum
- conferir que o nome da métrica aparece no endpoint — contador criado e nunca
  incrementado aparece igual

**Regra adotada:** todo teste imprime quantas coisas examinou. "505 linhas
lidas, 155 do logger acesso" é uma afirmação verificável; "ok" não é.

Aplicação disso nas métricas de negócio: o teste lê o valor **antes**, executa
a ação real, lê **depois**, e só passa se o número mexeu.

### 2. Nome de painel é o produto

Dois erros no mesmo painel, no mesmo dia:

- Chamado de **"Aplicação"**, mostrava "FORA" quando `up=0`. O usuário leu
  como sistema caído e foi conferir — estava no ar. O painel mede a métrica
  `up`, que é o Prometheus dizendo se **a raspagem dele** funcionou. Aplicação
  caída e Prometheus sem acesso dão o mesmo resultado, e daqui não dá para
  distinguir. Renomeado para **"Coleta de métricas"**.
- Os estados ficaram "COLETANDO" e "SEM COLETA". Gerúndio lê como ação em
  andamento, quando é o estado normal e permanente. Trocado para **"ATIVA"** e
  **"PARADA"**.

Nos dois casos o dado estava certo e a palavra estava errada. Num painel feito
para ser entendido em cinco segundos, a palavra é o produto.

### 3. O funil vale mais que o contador

`GET /form-templates/slug/{slug}` conta **abertura**.
`POST /form-submissions` conta **envio**.

| Aberturas | Envios | Leitura |
|---|---|---|
| normais | zero | formulário quebrado |
| zero | zero | ninguém acessou |

Contando só envio, os dois casos ficam idênticos. Os dois números lado a lado
na tela inicial, de propósito.

### 4. Métrica de negócio sabe o que o HTTP não sabe

Um agendamento recusado por slot lotado e um recusado por data inválida são,
do lado de fora, o mesmo 409 ou 400. Só o contador dentro do serviço separa.
E `slot_lotado` recorrente no mesmo horário não é problema técnico: é sinal de
que o cliente precisa de mais capacidade — conversa comercial.

## Pegadinhas técnicas registradas

### PromQL e Grafana

**Chave em f-string.** Seletor do Prometheus usa chave (`{job="x"}`) e f-string
do Python trata chave como interpolação. O gerador não usa f-string em nenhuma
expressão — só concatenação.

**NaN no topk.** `histogram_quantile` sobre buckets sem observação devolve NaN,
e NaN entra no `topk` ordenado **acima** de valores reais. Correção: `... > 0`
no fim — qualquer comparação com NaN é falsa.

**Actuator poluindo produto.** `/actuator/prometheus` é raspado a cada 15 s e
aparecia como endpoint mais lento e mais movimentado. Todo painel de produto
leva `uri!~"/actuator.*"`.

**allValue vazio.** Variável com `includeAll` e `allValue` vazio monta
`uri=~""`, que casa apenas com séries **sem** o rótulo — ou seja, nada. O
`allValue` correto é `.+`.

**uid sorteado.** Dashboard provisionado referencia fonte de dados por uid.
Deixando o Grafana sortear, cada máquina gera um uid diferente e todo painel
sobe com "datasource not found". Fixados: `prometheus-fabiano`, `loki-fabiano`.

**Consulta instantânea no Loki em tabela.** Devolve o conjunto de rótulos como
nome da coluna: a tabela sai com cabeçalho `{logger="com.cadastro...Auth"}` e
números soltos. Solução: painel de barras com `legendFormat` e
`renameByRegex` para cortar o pacote.

**`${__url_time_range}` nos links entre telas.** Sem ele, clicar num pico das
14h32 abre a outra tela nas últimas 6 horas e o pico se perde.

### Micrometer

**Timer com sufixo de unidade.** O Micrometer acrescenta `_seconds` sozinho.
`upload_duracao_segundos` vira `upload_duracao_segundos_seconds` e a query do
dashboard, escrita com o nome óbvio, não acha nada. Nomear sem sufixo.

**Contador nasce no primeiro incremento.** Não existe no endpoint antes disso.
Isso torna "painel vazio" ambíguo, e torna "17 séries encontradas" uma prova
de que 17 combinações foram realmente exercitadas.

**Facade com métodos `void`.** Injetar `MeterRegistry` direto nos serviços faz
o mock devolver `null` em `counter()` e estourar NPE nos testes. Uma fachada
com métodos void torna o mock inofensivo.

**Enum em vez de String para rótulo.** Torna impossível passar id por engano —
que é o erro de cardinalidade mais comum.

### Spring

**`@RestControllerAdvice` entra na fatia do `@WebMvcTest`.** Dar uma dependência
ao `GlobalExceptionHandler` fez **nove** testes de controller precisarem do
bean, não só os quatro de serviço. O erro aparece como falha de contexto do
Spring, não como erro de compilação.

**`server.tomcat.mbeanregistry.enabled=true`** é obrigatório para
`tomcat_threads_*` existir. Sem ele o painel de saturação do pool nasce vazio.

### Loki e Promtail

**O estágio `output` descarta o que não virou rótulo.** O pipeline terminava
com `output: source: mensagem`, depois de extrair `requestId` e `userId` — que
não viraram rótulo de propósito. Resultado: a correlação chegava ao Loki e
morria ali.

Correção: estágio `structured_metadata` antes do `output`. É o mecanismo certo
para campo de alta cardinalidade no Loki 3 com schema v13.

**Log de teste contaminando o painel.** Os testes rodam com perfil `dev` e
gravavam no mesmo `logs/app.json` que o Promtail entrega ao Loki. Cada
`mvnw test` despejava centenas de linhas no Grafana como se fossem da
aplicação. Correção: `logging.file.name` redirecionado no surefire, via
`systemPropertyVariables` (propriedade de sistema tem precedência sobre
arquivo).

## Auditoria de ruído de log (03/08)

508 linhas, 34 de WARN/ERROR, 8 classes. Categorizado por **frequência**, não
por volume:

| Aviso | Ocorrências | Frequência | Decisão |
|---|---|---|---|
| `PageImpl` | 9 | por resposta paginada | silenciado + FABIANO-36 |
| Flyway 8.4 | 7 | por inicialização | mantido (FABIANO-34) |
| `open-in-view` | 7 | por inicialização | declarado explícito + FABIANO-37 |
| `AuthService` | 7 | por tentativa de login | **mantido, é sinal** |
| 4 outros | 1 cada | resíduo de teste/histórico | resolvido pelo redirecionamento |

Depois da correção, medido cortando na última inicialização: **35 WARN antes,
0 depois**.

O `AuthService` não foi silenciado de propósito: são as tentativas de login
recusadas, o sinal que denuncia força bruta. Silenciar seria apagar o alarme
para a casa ficar quieta.

O Flyway também não: ele lembra que a versão em uso não foi testada contra o
MySQL 8.4, que é justamente para onde vamos. Uma vez por reinício é preço
aceitável por manter o lembrete visível.

## Achado de segurança — corrigido no mesmo dia

**FABIANO-35** — usuário inativo conseguia fazer login. O `AuthService`
detectava, escrevia no log que estava concedendo acesso indevido, e concedia.
Desligar um usuário é o botão de emergência; ele não fazia nada para o login, e
um admin desligado mantinha acesso pleno por 24 horas de token.

Terceiro caso do mesmo padrão no projeto: **o sistema reporta sucesso enquanto
não faz o que promete** — como o backup de 20 bytes e o `JWT_SECRET` com valor
padrão.

Correção aplicada em 03/08/2026:

* a flag `active` passou a ser conferida no `login()`, logo depois da senha
* a resposta é **idêntica** à de senha errada (`Invalid credentials`, HTTP 400).
  Dizer "usuário inativo" confirmaria para quem está de fora que aquele usuário
  existe — é enumeração de usuário. A distinção fica só no log e na métrica
* a métrica virou `auth_login_total{resultado="falha_usuario_inativo"}`. Como
  os painéis de proporção usam `resultado=~"falha.*"`, o caso passou a contar
  como falha automaticamente
* `active = NULL` continua valendo como **ativo**. A coluna nasceu
  `active BOOLEAN DEFAULT TRUE` na V1, aceitando nulo; ler nulo como inativo
  derrubaria usuários antigos que ninguém desligou

Antes de subir para produção é preciso conferir quantas contas estão hoje com
`active = 0` — essas pessoas vão perder o acesso na hora do deploy, e isso
precisa ser esperado, não descoberto:

```sql
SELECT id, username, email, role, active FROM users WHERE active = 0;
```

## Estado dos arquivos

```
observability/
  grafana/dashboards/fabiano-overview.json    Visao geral (altura 20, sem rolagem)
  grafana/dashboards/fabiano-produto.json     Uso do cliente, funil, negocio
  grafana/dashboards/fabiano-tecnico.json     HTTP, endpoints, JVM, banco, auth
  grafana/dashboards/fabiano-logs.json        Navegacao e busca
  grafana/provisioning/dashboards/dashboards.yml
  grafana/provisioning/datasources/datasources.yml
  prometheus/prometheus.yml
  promtail/promtail-config.yml
  loki/loki-config.yml

src/main/java/com/cadastro/fabiano/demo/config/
  MetricasDeNegocio.java      ponto unico de registro
  AccessLogFilter.java        uma linha por requisicao
  RequestIdFilter.java        requestId no MDC
  ObservabilityConfig.java    tags comuns e teto de cardinalidade
```

Gerador dos dashboards: `dash_lib.py` (helpers) + `gen_dash.py` (as telas).
Não versionado no repositório — vive fora, junto desta nota.

## Documento de apresentação ao cliente — MODELO DE REFERÊNCIA

`docs/observabilidade-fabiano.html`

Aprovado em 03/08/2026 como **padrão para toda documentação futura do
projeto destinada ao Fabiano**. Reutilizar a estrutura, não recomeçar.

### Regras que fazem o documento funcionar

**Escrito para quem não é técnico.** O cliente não precisa saber o que é
Prometheus. Precisa saber o que passou a enxergar. Nenhum nome de ferramenta
aparece no texto — só o que ela entrega.

**Nome do produto é Nexventa.** (A nota `PROJ-Fabiano-Cadastro-Formularios`
diz "FlexForm" e está errada — corrigir na origem.)

**Autocontido.** Zero recurso externo, zero CDN, zero `localStorage`. Abre
offline, por e-mail ou pen drive, em qualquer navegador.

**Antes e depois, não lista de recursos.** A pergunta que o cliente responde
não é "o que foi feito", é "o que mudou para mim".

**Interatividade que ensina, não que enfeita.** Três peças:
- o funil com três cenários clicáveis, que mostra por que dois números
  separados valem mais que um
- a linha de log dissecada, campo a campo, cada um explicando sua função
- o catálogo de tipos de registro, filtrável por gravidade, com três
  respostas por item: **quando sai**, **o que informa**, **o que fazer**

**Números que se provam.** Só entra número que veio de execução registrada.
Contadores animados chegando a 226, 54, 55, 77 — todos rastreáveis.

**Seção de pendências, obrigatória.** Documento que só mostra o que deu certo
não serve para o cliente decidir nada — e se ele descobrir a pendência sozinho
depois, perde-se mais do que se ganhou.

### Verificação antes de entregar

Renderizar em navegador de verdade (Playwright) e conferir: zero erro de
JavaScript no console, todos os elementos interativos respondendo, contadores
chegando aos valores certos, e nenhum recurso externo no HTML.

### Paleta e estrutura

Fundo `#0b0e14`, painéis `#161c2a`, bordas `#233046`. Verde `#3ddc97` para
bom, azul `#4c9aff` para uso, amarelo `#ffc857` para atenção, vermelho
`#ff5c6c` para falha — as mesmas cores dos painéis do Grafana, de propósito.

Seções: capa · antes e depois · as telas · o funil · a linha de log ·
catálogo de registros · como rastrear · o que já apareceu · números ·
pendências.


## Números de fechamento

- 226 testes unitários, 0 falhas
- 54 verificações no smoke, 0 falhas
- 55 painéis, 145 consultas, 135 validadas em parser PromQL
- 75 de 77 rotas cobertas por algum grupo de ação
- 0 sobreposições em 4056 células de grade

---
*Ver também: [[PROJ-Fabiano-Cadastro-Formularios]],
[[2026-08-03-Mapa-de-Endpoints-e-Dashboard-Grafana]],
[[2026-08-03-Cliente-MySQL84-na-EC2-e-Ensaio-em-Container]]*
