# Terraform — Projeto Fabiano

> **Nada aqui foi aplicado.** Estes arquivos descrevem a infraestrutura que já
> existe. O primeiro `apply` é um passo deliberado, com plan revisado, e está
> descrito abaixo.

## O que este diretório resolve

O Terraform trabalha com três peças: os arquivos `.tf` (o que deveria existir),
a infra real na AWS, e um **arquivo de estado** ligando as duas.

**O estado não existe neste projeto.** Consequência: o Terraform lê os `.tf`,
olha um estado vazio e conclui que nada existe. Um `apply` tentaria *criar* uma
EC2 e um RDS que já existem, e falharia na colisão de identificador.

Ou seja, até hoje o Terraform daqui **não controlava nada** — era documentação
com aparência de infraestrutura como código. E, pior, documentação **errada**:
o `main.tf` anterior declarava `backup_retention_period = 0` e
`skip_final_snapshot = true`, enquanto a realidade é 7 dias de retenção e
proteção contra exclusão. Um `apply` daquele arquivo teria **desligado o backup
automático do banco de produção**.

O ganho não é poder aplicar. É o `terraform plan` voltar a ser um **detector de
drift**: rodou e deu "No changes", ninguém mexeu pelo console.

## Estrutura

```
infra/terraform/
├── versions.tf              provider, versão mínima, backend S3 (comentado)
├── providers.tf             região; default_tags desligado até o 1º plan limpo
├── variables.tf             defaults = a realidade conferida na AWS
├── data.tf                  VPC default, AMI AL2023 (usada pelo módulo asg)
├── main.tf                  key pair, EC2, EIP, RDS de PRODUÇÃO
├── security_groups.tf       os dois SGs
├── imports.tf               blocos `import` — apaga depois do 1º apply
├── outputs.tf
├── bootstrap-state.sh       cria o bucket do state (execução única, fora do TF)
├── terraform.tfvars.example
├── user_data.sh             ARQUIVO HISTÓRICO, congelado — não editar
└── asg/                     módulo do Auto Scaling — INERTE (ninguém o chama)
```

> O diretório `asg/` **não é carregado** por `terraform plan` na raiz. Terraform
> só lê subdiretório que algum `module` referencia. Ele existe pronto para
> revisão, sem risco de entrar num apply por acidente.

## Ordem de execução — a primeira vez

```bash
# 1. Criar o bucket do state. Fora do Terraform de propósito: o bucket que
#    guarda o estado não pode ser gerenciado pelo estado que ele guarda.
./bootstrap-state.sh                      # no CloudShell

# 2. Descomentar o bloco backend "s3" em versions.tf

# 3. Inicializar
terraform init

# 4. PLANEJAR E LER. É aqui que o trabalho acontece.
terraform plan
```

O plan deve mostrar **os 6 imports e nenhuma outra mudança**. Se aparecer
qualquer `~` (alterar) ou `-/+` (recriar), **pare**.

> **Regra que não se negocia:** se o plan mostrar diferença, a correção vai no
> arquivo `.tf` para bater com a realidade — **nunca o contrário**.

```bash
# 5. Só depois de o plan estar limpo
terraform apply

# 6. Apagar imports.tf — ele é um passo de migração, não configuração
```

## As quatro armadilhas do import

Todas descobertas **antes** de tocar em qualquer coisa. Cada uma, encontrada no
meio de um `apply` sem contexto, viraria incidente.

| # | O quê | Por quê | Onde está a defesa |
|---|---|---|---|
| 1 | `description` de security group | A AWS **não permite alterar**. O provider trata como ForceNew → plan propõe **destruir e recriar o SG da EC2 de produção** | string exata mantida + `prevent_destroy` |
| 2 | `user_data` da EC2 | ForceNew. O arquivo do repositório foi editado depois do primeiro boot → plan propõe **recriar a EC2** | `ignore_changes = [user_data]` |
| 3 | `ami` da EC2 | ForceNew. A instância roda Amazon Linux **2**, não AL2023 como o comentário dizia | `ignore_changes = [ami]` |
| 4 | `password` do RDS | A API não devolve senha. O import deixa vazio e o plan propõe trocá-la | `ignore_changes = [password]` |

A **4** não estava no card — apareceu escrevendo estes arquivos, junto com uma
quinta: `public_key` do key pair tem o mesmo problema, e trocar a chave de um key
pair **recria o recurso**, o que invalidaria o acesso SSH à máquina de produção.
Também coberta com `ignore_changes`.

## O que NÃO é gerenciado aqui, e por quê

| Recurso | Motivo |
|---|---|
| `sg-0e19a5a773c15c01e` (default da VPC) | Não foi criado por este projeto e a AWS não deixa apagar |
| `i-008f8d272588845ef` (EC2 nova) | Vai ser substituída pelo ASG. Importar para remover depois = `destroy` no plan |
| `eipalloc-053acd67132fed0af` | Entra junto com o módulo `asg`, que é quem passa a associá-lo no boot |
| `poc-fabiano-db-ensaio` | Temporário — o FABIANO-48 prevê apagá-lo depois da virada |
| `poc-fabiano-mysql84` | Parameter group criado pela CLI, ainda não aplicado a nada (FABIANO-6) |

## Segurança

- **O state guarda a senha do banco em texto claro.** Por isso bucket privado,
  versionado, cifrado, com TLS obrigatório — e `.tfstate*` no `.gitignore`.
- O caminho definitivo é `manage_master_user_password`, que move a senha para o
  Secrets Manager e a tira do state. É uma alteração no RDS de produção e merece
  card próprio.

## O que ainda falta para o card fechar

- [ ] Rodar `bootstrap-state.sh` (precisa de credencial que crie bucket)
- [ ] `terraform init` + `plan` + revisão + `apply`
- [ ] Confirmar `plan` = "No changes" numa segunda execução
- [ ] Ligar `default_tags` num commit separado, depois do plan limpo
