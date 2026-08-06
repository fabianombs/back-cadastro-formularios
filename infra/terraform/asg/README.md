# Módulo `asg` — Auto Scaling da aplicação

> **Este módulo está INERTE.** Nenhum `module "asg"` o referencia, e o Terraform
> não carrega subdiretório que ninguém chama. Ele existe pronto para revisão.
>
> **Não pode ser aplicado ainda.** Os pré-requisitos abaixo não estão atendidos,
> e aplicar sem eles produziria uma máquina que sobe e não funciona — ou pior,
> destruiria estado que só existe no disco da instância atual.

## O que ele faz

`min = max = desired = 1`. Isso **não é escala horizontal** — é auto-recuperação:
instância morreu, outra nasce igual, com o mesmo endereço.

```
00:00  instância morre
00:02  ASG detecta e termina
00:03  nova instância sobe com o user_data
00:05  Docker instalado, segredos lidos do Parameter Store
00:07  imagem baixada do GHCR, compose no ar
00:08  Elastic IP reassociado, certificados emitidos
00:09  aplicação respondendo
```

RTO em torno de **10 minutos, automático**. Hoje é manual e leva horas — e
depende de alguém reconstruir o `.env` de memória.

## Por que não dá para aplicar hoje

| Bloqueio | Detalhe | Card |
|---|---|---|
| **Sem permissão de IAM** | `contato@resultatec.com.br` não cria role. Sem role, a instância não lê segredo nem escreve backup | FABIANO-20 |
| **Segredos só no disco** | O `.env` de produção existe em **um lugar no mundo**. Precisa ir para o Parameter Store antes | — |
| **Anexos antigos só no disco** | Uploads anteriores à migração para o S3 moram em `/app/uploads`. **ASG termina a instância, e o disco vai junto** | FABIANO-19 |
| **Sem pacote de deploy no S3** | Hoje os arquivos chegam por `scp` para uma máquina que já existe. Uma máquina nova nasce sem eles | card novo |

> **A terceira é a perigosa.** Ligar o ASG antes de tirar os anexos e os segredos
> do disco não é rede de proteção — é **automatizar a perda**. Hoje, se a máquina
> ficar insalubre por 5 minutos, alguém entra por SSH e conserta. Com ASG, ela é
> destruída antes disso.
>
> É a mesma armadilha que apareceu o dia todo, com consequência maior: a
> automação funciona perfeitamente, não dá erro nenhum, e faz a coisa errada.

## Ordem correta

| | O quê | Destrava |
|---|---|---|
| 1 | Role IAM (script `criar-role-fabiano.sh`) | tudo abaixo |
| 2 | Segredos no Parameter Store | instância deixa de ser insubstituível |
| 3 | Anexos e dumps 100% no S3 | disco deixa de guardar coisa única |
| 4 | CI publicando `deploy-bundle.tar.gz` no S3 | substituta nasce igual |
| 5 | **Aplicar este módulo** | morreu, volta sozinha |

Do 1 ao 4 tudo depende do passo 1.

## O que o `user_data` cobre

Exatamente o que o FABIANO-10 listou como "instalado à mão e em lugar nenhum do
código": `docker`, `docker-compose-plugin` (versão fixa), `mariadb105`
(`mysqldump`), `cronie`, `certbot`, os scripts de `/app`, a linha do cron e o
diretório do textfile collector.

E termina com **auto-verificação**, publicando `ProvisionamentoFalhas` no
CloudWatch. Sem isso, uma máquina que nasceu sem `mysqldump` ou sem cron parece
saudável: responde ping, responde HTTP, e só falha na madrugada em que o backup
deveria rodar — que foi exatamente o modo de falha do FABIANO-29.

## Pontos a conferir antes de aplicar

Escrevi estes três a partir da documentação, não do que está instalado hoje na
máquina. Vale conferir contra a realidade antes do primeiro apply:

1. **`certbot` via venv/pip.** O AL2023 não empacota certbot e não tem EPEL. Usei
   o método que a Let's Encrypt documenta. **Não sei como o certbot chegou na
   máquina atual** — se foi por outro caminho, vale alinhar.
2. **`mariadb105`** como origem do `mysqldump`. O card diz "mariadb"; o pacote
   exato do AL2023 é esse.
3. **Plugin do compose** baixado do GitHub em versão fixa (`v2.29.7`). Convém
   conferir contra a versão que roda hoje.

## Como ligar, quando chegar a hora

```hcl
module "asg_producao" {
  source = "./asg"

  ambiente           = "producao"
  security_group_ids = [aws_security_group.ec2.id]
  subnet_ids         = slice(data.aws_subnets.default.ids, 0, 2)  # 2 zonas
  eip_allocation_id  = "eipalloc-025082e8787508bb8"
  bucket_backup      = "fabiano-db-backups-135133927228"
  bucket_artefatos   = "fabiano-artefatos-135133927228"
  backend_tag        = var.backend_tag
  ghcr_owner         = var.ghcr_owner
  email_alertas      = "contato@resultatec.com.br"
}
```

E, no mesmo commit, remover `aws_instance.app` do `main.tf` — que é o passo que
o `prevent_destroy` obriga a fazer conscientemente.

## Sobre o ALB

`usar_alb = false` por padrão, e **não é economia**. Com ALB faz sentido ter mais
de uma instância, e mais de uma instância hoje não funciona: os anexos antigos
moram no disco de uma máquina só. Uma segunda serviria link quebrado para eles.

Com `health_check_type = "EC2"` o ASG só enxerga saúde de **instância**. Máquina
de pé com aplicação morta passa por saudável. Quem cobre esse caso hoje é o
alarme de HTTP externo — que vive fora da máquina, e é justamente o ponto.
