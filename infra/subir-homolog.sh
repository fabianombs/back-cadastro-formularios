#!/usr/bin/env bash
# =============================================================================
# Sobe o ambiente de homologacao (FABIANO-33)
# =============================================================================
# ONDE RODAR: CloudShell da AWS. Precisa de credencial de usuario IAM — a role
# da EC2 de producao NAO tem permissao para criar imagem, instancia nem banco,
# e isso e proposital.
#
# A TESE DO CARD, em uma frase:
#   Homolog que fica meses no ar DERIVA. Alguem mexe a mao para destravar um
#   teste, esquece, e seis meses depois o "espelho" nao espelha mais nada — e
#   passa a dar o falso teste que ele existia para evitar. Homolog recriado a
#   cada ciclo nao tem como derivar.
#
# Por isso ele nasce de:
#   - uma AMI da PRODUCAO DE HOJE, com --no-reboot (nao derruba o cliente)
#   - um SNAPSHOT do banco de producao de HOJE
#
# Ambiente montado do zero daria falso teste. Este projeto ja produziu tres
# provas disso: a coluna attendance_column_order que existe em producao e
# nenhuma migration cria (FABIANO-31), o mysqldump ausente na EC2 por meses
# (FABIANO-29), e o swap ausente causando OOM semanal. Nenhuma apareceria num
# ambiente provisionado a partir do Terraform.
#
# CUSTO: ~US$ 1/dia com tudo ligado. Zero depois do derrubar-homolog.sh.
# =============================================================================
set -euo pipefail

REGIAO=us-east-1

# --- producao, a fonte do espelho -------------------------------------------
# NAO fixar o ID da instancia. Desde 12/08 producao roda em Auto Scaling e a
# maquina e trocada sem aviso — o ID fixo daqui apontava para uma instancia ja
# terminada e o create-image morria com InvalidParameterValue.
#
# O que NAO muda e o Elastic IP. Nesta arquitetura, "a maquina de producao" e,
# por definicao, a que detem este endereco: quem tem o EIP recebe o trafego.
PROD_EIP=eipalloc-025082e8787508bb8          # 100.30.35.83, api e grafana
PROD_INSTANCIA=$(aws ec2 describe-addresses --region "$REGIAO" \
  --allocation-ids "$PROD_EIP" --query 'Addresses[0].InstanceId' --output text)
case "$PROD_INSTANCIA" in
  i-*) echo "producao detectada pelo EIP: $PROD_INSTANCIA" ;;
  *)   echo "FATAL: nenhuma instancia associada ao EIP de producao ($PROD_EIP)"
       exit 1 ;;
esac
PROD_BANCO=poc-fabiano-db

# --- homolog, criado neste script -------------------------------------------
HML_BANCO=poc-fabiano-homolog-db
HML_TIPO=t3.small
HML_CLASSE_BANCO=db.t3.micro
HML_CHAVE=poc-fabiano-homolog-key
HML_SG_EC2=sg-0800a897e3787c63d
HML_SG_RDS=sg-04be8eca0a2dc1578
HML_EIP=eipalloc-053acd67132fed0af          # 54.197.175.159, api-hml e grafana-hml
HML_SUBNET=subnet-0dfc383d51ee3004a         # us-east-1a, mesma de producao

# LIGADA POR PADRAO desde 10/08/2026 (FABIANO-33, item 3 do criterio de aceite).
#
# Ficou desligada no primeiro ciclo com o argumento de que o Mailpit captura todo
# e-mail, entao nenhuma mensagem chegaria a um cliente real. O argumento e
# verdadeiro e cobre UM vetor. O snapshot continua trazendo nome, e-mail e
# telefone reais para uma maquina com a porta 22 aberta para a internet.
#
# Desligar so e valido com 'nao' explicito na linha de comando — e ai a escolha
# fica no historico do shell de quem a fez, que e onde ela deve estar.
ANONIMIZAR="${ANONIMIZAR:-sim}"

CARIMBO=$(date +%Y%m%d-%H%M)
AMI_NOME="fabiano-homolog-base-${CARIMBO}"
SNAP_NOME="homolog-base-${CARIMBO}"

# O auto-desligamento vai embutido no user-data em base64. Assim, em vez de um
# heredoc dentro do heredoc, some o inferno de escapes — e continua havendo UMA
# fonte da verdade: o arquivo ao lado, versionado e revisavel.
AUTODESLIGA_ARQ="$(dirname "$0")/homolog-autodesliga.sh"
[ -f "$AUTODESLIGA_ARQ" ] || { echo "ERRO: nao encontrei ${AUTODESLIGA_ARQ}"; exit 1; }
AUTODESLIGA_B64=$(base64 -w0 "$AUTODESLIGA_ARQ")

msg() { echo -e "\n=== $* ==="; }

# -----------------------------------------------------------------------------
# 0. Guarda: nao subir dois homologs
# -----------------------------------------------------------------------------
# Sem isto, rodar o script duas vezes cria uma segunda EC2 que rouba o Elastic
# IP da primeira — e a primeira fica orfa, ligada, cobrando, e inalcancavel.
msg "0. conferindo se ja existe homolog"
JA=$(aws ec2 describe-instances --region "$REGIAO" \
  --filters "Name=tag:Ambiente,Values=homolog" "Name=instance-state-name,Values=pending,running,stopping,stopped" \
  --query 'Reservations[].Instances[].InstanceId' --output text)
if [ -n "$JA" ]; then
  echo "ERRO: ja existe EC2 de homolog: $JA"
  echo "      Rode ./derrubar-homolog.sh antes, ou use a que esta no ar."
  exit 1
fi
echo "nenhuma EC2 de homolog no ar, seguindo"

# -----------------------------------------------------------------------------
# 0b. Reconciliacao: apagar o que o auto-desligamento nao alcanca
# -----------------------------------------------------------------------------
# O homolog-autodesliga.sh mata SO a EC2 — ele nao tem papel IAM nenhum, e isso
# e proposital: a maquina nasce de uma imagem da producao e dar a ela permissao
# de apagar banco seria armar a arma do lado errado.
#
# Consequencia: banco, AMI e snapshots sobrevivem ao auto-desligamento. Sem este
# bloco, o SEGUNDO ciclo morre no passo 3 com DBInstanceAlreadyExists — e cada
# tentativa quebrada ainda deixa mais uma AMI e mais um snapshot para tras.
#
# Nao da para descobrir isso olhando o primeiro ciclo: ele passa. O defeito so
# aparece 24h depois, quando ninguem esta olhando.
msg "0b. limpando restos de ciclos anteriores"

if aws rds describe-db-instances --region "$REGIAO" \
     --db-instance-identifier "$HML_BANCO" >/dev/null 2>&1; then
  echo "banco orfao encontrado: ${HML_BANCO} — apagando"
  # --skip-final-snapshot: guardar um retrato final de homolog seria pagar
  # armazenamento por uma copia velha da producao que ninguem restauraria.
  aws rds delete-db-instance --region "$REGIAO" \
    --db-instance-identifier "$HML_BANCO" \
    --skip-final-snapshot --delete-automated-backups >/dev/null
  echo "aguardando o banco sumir (costuma levar 3-5 min)"
  aws rds wait db-instance-deleted --region "$REGIAO" --db-instance-identifier "$HML_BANCO"
  echo "banco orfao apagado"
else
  echo "nenhum banco orfao"
fi

for AMI_VELHA in $(aws ec2 describe-images --region "$REGIAO" --owners self \
    --filters "Name=tag:Ambiente,Values=homolog" \
    --query 'Images[].ImageId' --output text); do
  # Ler os snapshots ANTES do deregister. Depois dele a AMI some e os discos
  # ficam na fatura sem nada apontando para eles — foi assim que sobrou um
  # snapshot orfao do job que falhou em 09/08/2026.
  SNAPS=$(aws ec2 describe-images --region "$REGIAO" --image-ids "$AMI_VELHA" \
    --query 'Images[].BlockDeviceMappings[].Ebs.SnapshotId' --output text)
  aws ec2 deregister-image --region "$REGIAO" --image-id "$AMI_VELHA" >/dev/null
  echo "AMI antiga removida: ${AMI_VELHA}"
  for S in $SNAPS; do
    aws ec2 delete-snapshot --region "$REGIAO" --snapshot-id "$S" >/dev/null \
      && echo "  disco removido: ${S}" \
      || echo "  AVISO: nao consegui remover ${S} — confira na mao"
  done
done

for SNAP_VELHO in $(aws rds describe-db-snapshots --region "$REGIAO" --snapshot-type manual \
    --query "DBSnapshots[?starts_with(DBSnapshotIdentifier, 'homolog-base-')].DBSnapshotIdentifier" \
    --output text); do
  aws rds delete-db-snapshot --region "$REGIAO" \
    --db-snapshot-identifier "$SNAP_VELHO" >/dev/null \
    && echo "snapshot de banco antigo removido: ${SNAP_VELHO}" \
    || echo "AVISO: nao consegui remover ${SNAP_VELHO} — confira na mao"
done

# -----------------------------------------------------------------------------
# 1. Imagem da producao
# -----------------------------------------------------------------------------
msg "1. criando AMI da producao (--no-reboot)"
# --no-reboot e obrigatorio: sem ele a AWS REINICIA a instancia de producao para
# garantir consistencia de disco. O preco de nao reiniciar e que a imagem sai
# com o disco "sujo", como se a maquina tivesse levado um tapa na tomada — o
# que e aceitavel aqui, porque tudo que importa (banco) vem do snapshot RDS.
# As tags vao DENTRO da chamada de criacao, e nao num 'create-tags' depois.
#
# Nao e estilo: a politica IAM do papel do GitHub permite ec2:CreateTags apenas
# com a condicao ec2:CreateAction in [RunInstances, CreateImage] — e essa chave
# de contexto SO existe quando a etiqueta viaja junto com a chamada que cria o
# recurso. Num 'create-tags' avulso ela nem aparece, a condicao nunca casa, e a
# AWS recusa com UnauthorizedOperation.
#
# Aconteceu no primeiro ciclo pela esteira, em 09/08/2026: a AMI foi criada e o
# job morreu na linha seguinte, deixando uma imagem sem etiqueta para tras.
#
# A alternativa seria afrouxar a politica para permitir CreateTags em qualquer
# recurso — o que deixaria a esteira reetiquetar producao, inclusive marcar
# producao como 'homolog' e fazer o auto-desligamento apontar a arma para o
# lado errado. Etiquetar na criacao custa uma linha e mantem a trava.
AMI=$(aws ec2 create-image --region "$REGIAO" \
  --instance-id "$PROD_INSTANCIA" \
  --name "$AMI_NOME" \
  --description "Base de homolog a partir da producao em ${CARIMBO} (FABIANO-33)" \
  --tag-specifications 'ResourceType=image,Tags=[{Key=Project,Value=poc-fabiano},{Key=Ambiente,Value=homolog}]' \
                       'ResourceType=snapshot,Tags=[{Key=Project,Value=poc-fabiano},{Key=Ambiente,Value=homolog}]' \
  --no-reboot --query ImageId --output text)
echo "AMI: $AMI"

# -----------------------------------------------------------------------------
# 2. Snapshot do banco (em paralelo com a AMI, que demora mais)
# -----------------------------------------------------------------------------
msg "2. criando snapshot do banco de producao"
# Snapshot e nao dump: garante versao de engine, charset, collation, indices,
# flyway_schema_history, contagem e distribuicao IDENTICOS. Migration garante so
# o que as migrations sabem — e o FABIANO-31 provou que elas nao sabem tudo.
aws rds create-db-snapshot --region "$REGIAO" \
  --db-instance-identifier "$PROD_BANCO" \
  --db-snapshot-identifier "$SNAP_NOME" \
  --tags Key=Project,Value=poc-fabiano Key=Ambiente,Value=homolog \
  --query 'DBSnapshot.[DBSnapshotIdentifier,Status]' --output text

msg "aguardando snapshot ficar disponivel (costuma levar 3-6 min)"
aws rds wait db-snapshot-available --region "$REGIAO" --db-snapshot-identifier "$SNAP_NOME"
echo "snapshot pronto"

# -----------------------------------------------------------------------------
# 3. Restaurar como banco de homolog
# -----------------------------------------------------------------------------
msg "3. restaurando o snapshot como ${HML_BANCO}"
GRUPO_SUBNET=$(aws rds describe-db-instances --region "$REGIAO" \
  --db-instance-identifier "$PROD_BANCO" \
  --query 'DBInstances[0].DBSubnetGroup.DBSubnetGroupName' --output text)
GRUPO_PARAM=$(aws rds describe-db-instances --region "$REGIAO" \
  --db-instance-identifier "$PROD_BANCO" \
  --query 'DBInstances[0].DBParameterGroups[0].DBParameterGroupName' --output text)
echo "subnet group: ${GRUPO_SUBNET} | parameter group: ${GRUPO_PARAM}"

# backup-retention-period 0: homolog nao precisa de PITR, e retencao ligada
# custaria armazenamento por algo que sera apagado em dias.
aws rds restore-db-instance-from-db-snapshot --region "$REGIAO" \
  --db-instance-identifier "$HML_BANCO" \
  --db-snapshot-identifier "$SNAP_NOME" \
  --db-instance-class "$HML_CLASSE_BANCO" \
  --db-subnet-group-name "$GRUPO_SUBNET" \
  --db-parameter-group-name "$GRUPO_PARAM" \
  --vpc-security-group-ids "$HML_SG_RDS" \
  --no-publicly-accessible \
  --no-multi-az \
  --tags Key=Project,Value=poc-fabiano Key=Ambiente,Value=homolog \
  --query 'DBInstance.[DBInstanceIdentifier,DBInstanceStatus]' --output text

msg "aguardando a AMI ficar disponivel"
aws ec2 wait image-available --region "$REGIAO" --image-ids "$AMI"
echo "AMI pronta"

msg "aguardando o banco de homolog ficar disponivel (costuma levar 8-12 min)"
aws rds wait db-instance-available --region "$REGIAO" --db-instance-identifier "$HML_BANCO"

HML_ENDPOINT=$(aws rds describe-db-instances --region "$REGIAO" \
  --db-instance-identifier "$HML_BANCO" \
  --query 'DBInstances[0].Endpoint.Address' --output text)
echo "banco de homolog: ${HML_ENDPOINT}"

# -----------------------------------------------------------------------------
# 4. A EC2, com toda a configuracao no user-data
# -----------------------------------------------------------------------------
# Por que user-data e nao SSH: a chave privada de homolog vive na maquina do
# desenvolvedor, e este script roda na CloudShell. Alem disso, o que rodou fica
# registrado no proprio launch — auditavel sem depender de log de terminal.
msg "4. subindo a EC2 de homolog"

USER_DATA=$(cat <<CLOUDINIT
#!/bin/bash
exec > /var/log/homolog-init.log 2>&1
set -x

# --- 0. PARAR TUDO ANTES DE QUALQUER COISA -----------------------------------
# A maquina nasce com o .env de PRODUCAO, e o Compose tem politica de restart:
# os containers sobem no boot, antes deste script terminar. O Security Group de
# homolog ja impede a conexao com o banco de producao (o SG do RDS de producao
# so aceita o SG da EC2 de producao), mas duas defesas para o mesmo risco sao
# baratas — e a segunda nao depende de ninguem lembrar de nunca mexer no SG.
cd /home/ec2-user/fabiano/deploy
# SAO DOIS PROJETOS COMPOSE, nao um. O de observabilidade e projeto proprio que
# se conecta a rede do principal — por isso a esteira faz um
# 'docker compose -f docker-compose.observability.yml restart' em chamada
# separada. Juntar os dois com '-f -f' cria um TERCEIRO projeto: o down nao
# derruba nada, os containers herdados da AMI ficam de pe, e o up seguinte morre
# com "container name is already in use". Custou meia hora no primeiro ciclo.
docker compose -f docker-compose.observability.yml down --remove-orphans || true
docker compose down --remove-orphans || true

# --- 1. crontab: fora o backup, fica a renovacao -----------------------------
# CRITICO: o cron de backup herdado manda e-mail COM ANEXO DO BANCO para o
# cliente. Rodando em homolog, o Fabiano receberia um "backup" que nao e o dele.
crontab -r 2>/dev/null || true
echo "17 3 * * * /app/renovar-certificados.sh" | crontab -
# O renovar-certificados.sh descobre sozinho quais certificados apontam para
# esta maquina — aqui vai renovar api-hml e grafana-hml, e pular os de producao.

# --- 1b. segredos de producao que moram FORA de deploy/ ----------------------
# O /etc/fabiano-backup.env carrega SMTP_USER e SMTP_PASS de producao e vem
# dentro da AMI, entao toda homolog nascia com ele. A sanitizacao do passo 3 so
# alcanca o .env do Compose — este arquivo escapava por estar fora de deploy/.
#
# Nao esta em uso aqui (o crontab acima foi substituido e a homolog nao roda o
# backup-db.sh), mas esta maquina tem a 22 aberta e vive dias. Credencial de
# producao parada tambem vaza.
#
# Sobrescrever, e nao apagar: se algum script herdado ainda fizer 'source' dele,
# a ausencia do arquivo quebraria o boot com erro obscuro; com valores inertes
# ele carrega e nao entrega nada. .invalid e reservado pela RFC 2606.
if [ -f /etc/fabiano-backup.env ]; then
  cat > /etc/fabiano-backup.env <<'INERTE'
# Sanitizado pelo subir-homolog.sh (FABIANO-79). Os valores de producao que
# vinham na AMI foram substituidos por valores que nao autenticam em lugar
# nenhum. Nao repor credencial real aqui.
SMTP_USER=homolog@exemplo.invalid
SMTP_PASS=homolog
MAIL_TO=homolog@exemplo.invalid
INERTE
  chmod 600 /etc/fabiano-backup.env
fi

# Conferir depois de transformar, e falhar ALTO. Sem esta guarda, um arquivo com
# formato diferente do esperado passaria silenciosamente com a senha intacta.
if grep -qiE 'nexventa|gmail|sendgrid|smtp\.' /etc/fabiano-backup.env 2>/dev/null; then
  echo "ERRO: /etc/fabiano-backup.env ainda parece conter valor de producao." >&2
  exit 1
fi

# Varredura do resto da maquina. Imprime NOMES DE ARQUIVO, nunca conteudo: o
# objetivo e descobrir onde procurar, e um grep que ecoa a senha na tela do
# cloud-init transformaria a busca por vazamento em um vazamento.
echo "--- arquivos fora de deploy/ que parecem carregar segredo ---"
grep -rlIE 'AKIA[0-9A-Z]{16}|SMTP_PASS=|DB_PASSWORD=|SECRET=' \
  /etc /usr/local/bin /opt 2>/dev/null \
  | grep -v '^/etc/fabiano-backup.env\$' || echo "  nenhum"

# --- 2. certificados: os de producao saem daqui ------------------------------
# A AMI carrega /etc/letsencrypt inteiro, inclusive a CHAVE PRIVADA dos
# certificados de producao. Homolog e menos vigiado e tem a 22 aberta: material
# criptografico de producao nao pode morar aqui.
#
# Mas apagar e so metade: o nginx.conf tem server block para api. e grafana.
# apontando para esses arquivos, e nginx com ssl_certificate inexistente NAO
# SOBE — homolog inteiro morreria no boot. Por isso, autoassinado no lugar.
for D in api.nexventa.com.br grafana.nexventa.com.br 100-30-35-83.sslip.io; do
  certbot delete --cert-name "\$D" --non-interactive || true
  mkdir -p "/etc/letsencrypt/live/\$D"
  openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
    -keyout "/etc/letsencrypt/live/\$D/privkey.pem" \
    -out    "/etc/letsencrypt/live/\$D/fullchain.pem" \
    -subj "/CN=\$D-NAO-USAR-ESTE-E-HOMOLOG"
  cp "/etc/letsencrypt/live/\$D/fullchain.pem" "/etc/letsencrypt/live/\$D/chain.pem"
done

# --- 3. o .env de homolog ----------------------------------------------------
cd /home/ec2-user/fabiano/deploy
cp .env .env.veio-da-producao

trocar() { grep -q "^\$1=" .env && sed -i "s|^\$1=.*|\$1=\$2|" .env || echo "\$1=\$2" >> .env; }

trocar DB_HOST "${HML_ENDPOINT}"
trocar SPRING_PROFILES_ACTIVE homolog
trocar APP_BASE_URL https://api-hml.nexventa.com.br
trocar APP_FRONTEND_URL https://hml.nexventa.com.br
# O curinga entra SO aqui, nunca em producao: o .env.example ja registrava a
# regra, e o SecurityConfig usa setAllowedOriginPatterns, entao '*' funciona.
# Sem ele, cada preview da Vercel nasce num subdominio novo e o navegador
# barra o /auth/login por CORS — a rota e publica no Spring Security, mas o
# CORS dela e o 'privateConfig', que so aceita a lista.
# Em producao isso seria grave: qualquer projeto Vercel do mundo poderia
# chamar a API autenticada pelo navegador de um usuario logado.
trocar CORS_ALLOWED_ORIGINS 'https://hml.nexventa.com.br,https://*.vercel.app'
trocar GRAFANA_ROOT_URL https://grafana-hml.nexventa.com.br

# JWT proprio: com o mesmo segredo de producao, um token emitido em homolog
# seria aceito em producao. Nao e teorico — e a mesma chave assinando os dois.
trocar JWT_SECRET "\$(openssl rand -base64 48 | tr -d '\n')"

# Mailpit: SMTP local que ACEITA tudo e NAO ENTREGA nada. Melhor que desligar o
# e-mail, porque o fluxo continua testavel — a mensagem chega numa caixa web em
# vez da caixa do cliente.
trocar SMTP_HOST mailpit
trocar SMTP_PORT 1025
# NAO deixar vazio. Duas armadilhas encadeadas, as duas pagas no primeiro ciclo:
#   1. o docker-compose.observability.yml usa \${SMTP_USER:?...}, que trata
#      VAZIO como AUSENTE e aborta o 'up' inteiro — nao subiu container nenhum.
#   2. o Grafana usa esse mesmo valor como GF_SMTP_FROM_ADDRESS, entao ele
#      precisa ter FORMATO DE E-MAIL. Com 'homolog' sozinho ele sobe, valida,
#      e cai em restart loop com "invalid email address for SMTP from_address".
# .invalid e reservado pela RFC 2606: o dominio nunca vai existir, entao nem por
# engano uma mensagem sairia dali para algum lugar real.
trocar SMTP_USER homolog@exemplo.invalid
trocar SMTP_PASS homolog
trocar ALERTA_EMAIL homolog@exemplo.invalid
trocar ALERTA_SUBMISSAO_PAUSADO true

# --- S3: armario proprio, e sem a chave de producao --------------------------
# MEDIDO EM 10/08/2026: a homolog nascia com AWS_S3_BUCKET=cadastro-fabiano-uploads
# e com a MESMA chave estatica de producao (AKIA...Z2UT), herdadas da AMI. Ou seja:
# todo upload feito em homologacao gravava no bucket do cliente, e como o
# S3ImageStorageService tambem chama deleteObject, um teste de troca de imagem
# podia APAGAR uma foto de producao.
#
# Pior: aquele usuario IAM tem AmazonS3FullAccess. A chave nesta maquina alcancava
# tambem o bucket de BACKUP DO BANCO e o de artefatos de deploy — numa maquina de
# teste, com a porta 22 aberta, recriada a cada ciclo.
#
# Nao foi falha de desenho: a sanitizacao protege o que alguem lembrou de listar.
# Banco, e-mail e JWT foram lembrados; o S3 nao. E era invisivel porque a homolog
# FUNCIONA assim — o upload sobe, a imagem aparece, nada acusa.
trocar AWS_S3_BUCKET cadastro-fabiano-uploads-hml
trocar AWS_S3_BASE_URL https://cadastro-fabiano-uploads-hml.s3.us-east-1.amazonaws.com

# As chaves saem do arquivo, nao viram valor falso. Com o DefaultCredentialsProvider
# (FABIANO-79) a aplicacao so cai no papel da instancia quando as variaveis NAO
# EXISTEM: um valor invalido seria encontrado pela cadeia e usado, resultando em
# 403 na hora do upload em vez de usar o papel.
sed -i '/^AWS_ACCESS_KEY_ID=/d; /^AWS_SECRET_ACCESS_KEY=/d' .env

# Conferir depois de transformar. Um 'sed' que nao casa nada sai com 0 e deixa o
# arquivo intacto — e esta guarda e a unica coisa entre um ciclo silenciosamente
# errado e a producao.
if grep -qE '^AWS_(ACCESS_KEY_ID|SECRET_ACCESS_KEY)=' .env; then
  echo "ABORTADO: sobrou chave estatica da AWS no .env da homolog." >&2
  exit 1
fi
if ! grep -q '^AWS_S3_BUCKET=cadastro-fabiano-uploads-hml$' .env; then
  echo "ABORTADO: o AWS_S3_BUCKET da homolog NAO ficou apontando para o bucket de homologacao." >&2
  echo "          Sem isto, esta maquina grava e apaga no bucket do cliente." >&2
  exit 1
fi
echo "    S3 da homolog: bucket proprio, sem chave estatica."

# --- 4. anonimizacao ---------------------------------------------------------
# Roda ANTES do 'docker compose up' do passo 6, de proposito: nao pode existir
# uma janela em que a API esteja no ar servindo dado real.
#
# COLUNAS CONFERIDAS CONTRA O SCHEMA REAL em 10/08/2026, nao supostas. A versao
# anterior mexia em tres colunas escolhidas de cabeca e imprimia "anonimizado:
# sim" — teria deixado todos os NOMES intactos e ninguem saberia.
#
# NAO EXISTE COLUNA DE CPF neste banco. Nem CNPJ, nem data de nascimento, nem
# endereco. O criterio do card foi escrito supondo que existisse.
#
# EXCECAO DELIBERADA: 'username' fica intacto. Ele nao e e-mail em nenhum dos 34
# registros (conferido), e serve de chave de login — trocar inutilizaria a
# homolog para teste. O que causa dano num vazamento e dado de CONTATO: nome,
# e-mail, telefone. Um handle de login sozinho, sem nada disso ao lado, nao e.
#
# NAO SAO ANONIMIZADOS os 'name' de equipment_catalogs, form_templates,
# quiz_configs e survey_configs: sao nomes de COISAS. Mexer neles destruiria o
# ambiente de teste sem proteger pessoa nenhuma.
if [ "${ANONIMIZAR}" = "sim" ]; then
  set -a; . ./.env; set +a

  # Sem --force: o mysql para no primeiro erro. Se uma tabela mudar de nome numa
  # migration futura, o certo e falhar aqui e nao seguir com dado real.
  MYSQL_PWD="\$DB_PASSWORD" mysql -h "\$DB_HOST" -u "\$DB_USER" "\$DB_NAME" <<'SQL'
-- Preserva NULL como NULL: campo vazio e caso de teste, e trocar por texto
-- esconderia bugs que so aparecem com valor ausente.
-- Substituicao derivada do id: preserva contagem, unicidade, indices e tamanho
-- de tabela — o teste de desempenho continua valendo.
UPDATE users                 SET email           = CONCAT('usuario', id, '@exemplo.invalid') WHERE email IS NOT NULL;
UPDATE users                 SET name            = CONCAT('Usuario ', id)                    WHERE name IS NOT NULL;
UPDATE clients               SET email           = CONCAT('cliente', id, '@exemplo.invalid') WHERE email IS NOT NULL;
UPDATE clients               SET name            = CONCAT('Cliente ', id)                    WHERE name IS NOT NULL;
UPDATE clients               SET phone           = CONCAT('5511', LPAD(id, 9, '0'))          WHERE phone IS NOT NULL;
UPDATE attendance_companions SET name            = CONCAT('Acompanhante ', id)               WHERE name IS NOT NULL;
UPDATE attendance_companions SET phone           = CONCAT('5511', LPAD(id, 9, '0'))          WHERE phone IS NOT NULL;
UPDATE appointments          SET booked_by_name  = CONCAT('Reserva ', id)                    WHERE booked_by_name IS NOT NULL;
UPDATE quiz_sessions         SET player_name     = CONCAT('Jogador ', id)                    WHERE player_name IS NOT NULL;
SQL
  CODIGO_SQL=\$?

  # A CONFERENCIA E A PARTE QUE IMPORTA.
  #
  # Um UPDATE que nao casa nenhuma linha devolve SUCESSO. Um WHERE que deixou de
  # bater depois de uma migration devolve sucesso. Sem contar o que sobrou, este
  # bloco imprimiria "anonimizado: sim" sobre um banco intacto — que e
  # exatamente a familia de defeito que este projeto passou a semana caçando.
  SOBROU=\$(MYSQL_PWD="\$DB_PASSWORD" mysql -N -B -h "\$DB_HOST" -u "\$DB_USER" "\$DB_NAME" -e "
    SELECT
      (SELECT COUNT(*) FROM users                 WHERE email          IS NOT NULL AND email          NOT LIKE '%@exemplo.invalid')
    + (SELECT COUNT(*) FROM users                 WHERE name           IS NOT NULL AND name           NOT LIKE 'Usuario %')
    + (SELECT COUNT(*) FROM clients               WHERE email          IS NOT NULL AND email          NOT LIKE '%@exemplo.invalid')
    + (SELECT COUNT(*) FROM clients               WHERE name           IS NOT NULL AND name           NOT LIKE 'Cliente %')
    + (SELECT COUNT(*) FROM clients               WHERE phone          IS NOT NULL AND phone          NOT LIKE '5511%')
    + (SELECT COUNT(*) FROM attendance_companions WHERE name           IS NOT NULL AND name           NOT LIKE 'Acompanhante %')
    + (SELECT COUNT(*) FROM attendance_companions WHERE phone          IS NOT NULL AND phone          NOT LIKE '5511%')
    + (SELECT COUNT(*) FROM appointments          WHERE booked_by_name IS NOT NULL AND booked_by_name NOT LIKE 'Reserva %')
    + (SELECT COUNT(*) FROM quiz_sessions         WHERE player_name    IS NOT NULL AND player_name    NOT LIKE 'Jogador %')
  " 2>/dev/null)

  if [ "\$CODIGO_SQL" -ne 0 ] || [ -z "\$SOBROU" ] || [ "\$SOBROU" != "0" ]; then
    echo "ANONIMIZACAO FALHOU: mysql saiu \$CODIGO_SQL, restaram '\${SOBROU:-?}' campos com dado original."
    echo "A maquina NAO vai subir com dado real exposto."
    echo "FALHOU: anonimizacao" > /var/log/homolog-init.FALHOU
    exit 1
  fi
  echo "anonimizacao conferida: 0 campos com dado original"
fi

# --- 5. observabilidade: parar de vigiar PRODUCAO ----------------------------
# A AMI traz o prometheus.yml com os alvos do blackbox apontando para os nomes
# de PRODUCAO. Sem esta correcao, o Grafana de homolog mostra a saude de
# producao (voce olha para hml e enxerga prod), os alertas de homolog disparam
# sobre producao, e homolog fica batendo em producao a cada 30 segundos.
#
# A linha do sslip.io SAI em vez de ser trocada: aquele nome deriva do IP de
# PRODUCAO, entao em homolog ele nao tem substituto — sonda a maquina errada
# por definicao.
#
# Redirecionamento e nao 'sed -i': estes arquivos sao bind mount de arquivo
# unico. 'sed -i' cria inode novo e o container fica lendo a versao antiga para
# sempre — a armadilha que congelou o nginx na virada (FABIANO-47).
cp observability/prometheus.yml /tmp/prom.orig
sed -e '\|https://100-30-35-83\.sslip\.io/actuator/health|d' \
    -e 's|https://api\.nexventa\.com\.br|https://api-hml.nexventa.com.br|' \
    -e 's|https://grafana\.nexventa\.com\.br|https://grafana-hml.nexventa.com.br|' \
    /tmp/prom.orig > observability/prometheus.yml

# Sem isto os logs de homolog se declaram producao — e o alerta
# fabiano-coleta-log-parada vigia justamente {ambiente="prod"}.
# Substituicao LITERAL e nao variavel: o proprio promtail-config.yml avisa que
# variavel indefinida vira rotulo vazio, e fluxo sem par de rotulo faz o Loki
# recusar o lote inteiro com 400 (FABIANO-69).
cp observability/promtail-config.yml /tmp/promtail.orig
sed -e "s|replacement: 'prod'|replacement: 'homolog'|" \
    -e "s|ambiente: prod|ambiente: homolog|" \
    /tmp/promtail.orig > observability/promtail-config.yml

# --- 6. subir ----------------------------------------------------------------
# --pull never: a imagem do backend JA ESTA no disco, herdada da producao junto
# com 15 versoes anteriores. O compose tem 'pull_policy: always', que esta certo
# para producao (garante que sobe o que esta no registry) e errado num boot frio
# a partir de AMI — o token do GHCR que a esteira injeta e de curta duracao e ja
# expirou, entao o pull morre com "denied: denied" tendo a imagem ao lado.
# Os deploys seguintes vem da esteira, que faz docker login com token novo.
docker compose up -d --pull never
RESULTADO_APP=\$?

docker compose -f docker-compose.observability.yml up -d
RESULTADO_OBS=\$?

REDE=\$(docker inspect -f '{{range \$k,\$v := .NetworkSettings.Networks}}{{\$k}}{{end}}' fabiano-backend)
docker rm -f mailpit 2>/dev/null || true
# -p 127.0.0.1:8025:8025 e obrigatorio, e o endereco importa tanto quanto a porta.
#
# Sem publicar, a 8025 so existe dentro da rede do Docker: o tunel SSH nao chega
# nela, porque quem resolve o destino de um -L e o servidor SSH, que roda no
# host — e o host nao conhece nomes de container.
#
# Publicar em 127.0.0.1 e nao em 0.0.0.0 e o que mantem a caixa fora da internet.
# Homolog tem a 22 aberta e recebe copia dos dados de producao; uma caixa de
# e-mails capturados acessivel de fora seria pior que o problema que ela resolve.
docker run -d --name mailpit --network "\$REDE" \
  -p 127.0.0.1:8025:8025 \
  --restart unless-stopped axllent/mailpit

docker exec fabiano-nginx nginx -s reload || true

# --- 7. o marcador so vale se for verdade ------------------------------------
# A primeira versao deste script escrevia CONCLUIDO incondicionalmente. No
# primeiro ciclo o 'up' falhou, NADA subiu, e o marcador disse que tinha
# terminado — o mesmo padrao de sinal verde mentiroso que custou caro em todo o
# resto deste projeto. Agora ele so aparece se os dois projetos subiram.
if [ "\$RESULTADO_APP" -eq 0 ] && [ "\$RESULTADO_OBS" -eq 0 ]; then
  touch /var/log/homolog-init.CONCLUIDO
else
  touch /var/log/homolog-init.FALHOU
fi

# --- 8. auto-desligamento por inatividade ------------------------------------
# Instalado POR ULTIMO, de proposito: se o boot falhar antes daqui, a maquina
# fica de pe para ser investigada em vez de sumir sozinha com a evidencia.
echo '${AUTODESLIGA_B64}' | base64 -d > /usr/local/bin/homolog-autodesliga.sh
chmod 755 /usr/local/bin/homolog-autodesliga.sh
( crontab -l 2>/dev/null; echo "*/15 * * * * /usr/local/bin/homolog-autodesliga.sh" ) | crontab -
CLOUDINIT
)

# Um RunInstances cria varios recursos de uma vez — instancia, volume e placa de
# rede — e a AWS avalia a permissao de CADA UM separadamente. O volume tambem
# precisa nascer etiquetado: a politica IAM exige Ambiente=homolog nele, e sem
# esta segunda linha de tag a chamada inteira e negada com UnauthorizedOperation
# em volume/*. Aconteceu no ciclo de 10/08/2026.
#
# POR QUE A HOMOLOG PASSOU A TER PAPEL DE INSTANCIA (mudanca de decisao, 10/08)
#
# Ate aqui a homolog nascia SEM papel nenhum, de proposito: sem papel, ela nao
# alcancava o S3 nem o SSM de producao. A intencao estava certa e o efeito era o
# oposto — sem papel, o que fazia o upload funcionar era a chave estatica de
# producao herdada da AMI, e aquele usuario tem AmazonS3FullAccess. Na pratica a
# maquina "sem permissao" tinha acesso total a TODOS os buckets da conta,
# inclusive o de backup do banco.
#
# O papel 'poc-fabiano-homolog-ec2' tem exatamente dois blocos: as tres acoes do
# S3ImageStorageService restritas ao bucket de homologacao, e o SSM gerenciado
# (sem ele esta maquina nasce sem acesso remoto). Nada de producao.
#
# Um papel restrito e estritamente melhor que uma chave ampla no disco. A decisao
# antiga nao foi revertida — foi cumprida de verdade pela primeira vez.
#
# ATENCAO: passar --iam-instance-profile exige iam:PassRole no papel de quem roda
# este script. Sem isso o RunInstances falha com UnauthorizedOperation, e a
# mensagem fala em PassRole, nao em perfil de instancia.
INSTANCIA=$(aws ec2 run-instances --region "$REGIAO" \
  --image-id "$AMI" \
  --instance-type "$HML_TIPO" \
  --key-name "$HML_CHAVE" \
  --security-group-ids "$HML_SG_EC2" \
  --subnet-id "$HML_SUBNET" \
  --user-data "$USER_DATA" \
  --metadata-options "HttpTokens=required,HttpEndpoint=enabled" \
  --instance-initiated-shutdown-behavior terminate \
  --iam-instance-profile "Name=poc-fabiano-homolog-ec2" \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=poc-fabiano-homolog},{Key=Project,Value=poc-fabiano},{Key=Ambiente,Value=homolog}]" \
                       "ResourceType=volume,Tags=[{Key=Name,Value=poc-fabiano-homolog},{Key=Project,Value=poc-fabiano},{Key=Ambiente,Value=homolog}]" \
  --query 'Instances[0].InstanceId' --output text)
echo "instancia: $INSTANCIA"

msg "aguardando a instancia responder"
aws ec2 wait instance-running --region "$REGIAO" --instance-ids "$INSTANCIA"

msg "5. associando o Elastic IP de homolog"
aws ec2 associate-address --region "$REGIAO" \
  --allocation-id "$HML_EIP" --instance-id "$INSTANCIA" \
  --query 'AssociationId' --output text

cat <<FIM

=============================================================================
 HOMOLOG NO AR
=============================================================================
 instancia   : ${INSTANCIA}
 banco       : ${HML_ENDPOINT}
 AMI base    : ${AMI}
 snapshot    : ${SNAP_NOME}
 anonimizado : ${ANONIMIZAR}

 API         : https://api-hml.nexventa.com.br
 Grafana     : https://grafana-hml.nexventa.com.br
 Frontend    : https://hml.nexventa.com.br
 SSH         : ssh -i ~/.ssh/poc-fabiano-homolog ec2-user@54.197.175.159

 O user-data leva 2-4 min depois do boot. Conferir que terminou BEM:
   ssh ... 'ls -l /var/log/homolog-init.CONCLUIDO /var/log/homolog-init.FALHOU 2>&1 | tail -2'
 CONCLUIDO = os dois projetos Compose subiram. FALHOU = ver o log:
   ssh ... 'tail -60 /var/log/homolog-init.log'

 Validar DE FORA, que e o que conta:
   curl -sS -o /dev/null -w 'api-hml %{http_code} TLS %{ssl_verify_result}\n' \\
     https://api-hml.nexventa.com.br/actuator/health
 TLS 0 significa certificado VALIDADO. Se estivesse servindo o autoassinado
 dos nomes de producao, o curl recusaria a conexao.

 E-mails: NENHUM sai da maquina. Caixa do Mailpit:
   ssh -i ~/.ssh/poc-fabiano-homolog -L 8025:localhost:8025 ec2-user@54.197.175.159
   e abrir http://localhost:8025 no navegador da sua maquina

 -----------------------------------------------------------------------------
 !! FALTA UM PASSO, E ELE NAO E OPCIONAL !!
 -----------------------------------------------------------------------------
 GitHub > Settings > Secrets and variables > Actions > aba VARIABLES
   HOMOLOG_ATIVO = true

 Sem isso o job 'Deploy em homologacao' e PULADO, e o 'homolog-desativada'
 emite aviso amarelo — homolog fica de pe sem receber deploy nenhum.
 Atencao: e VARIABLE, nao Secret. Como secret, vars.HOMOLOG_ATIVO vem vazio e
 o 'if' nunca casa.

 AUTO-DESLIGAMENTO: esta maquina se TERMINA sozinha se ficar
   - 24h sem nenhuma requisicao real (a sonda do blackbox nao conta), ou
   - 7 dias de pe, aconteca o que acontecer.
 Sessao SSH aberta segura o desligamento. Registro em:
   /var/log/homolog-autodesliga.log

 Para derrubar agora e zerar o custo:
   ./derrubar-homolog.sh
=============================================================================
FIM
