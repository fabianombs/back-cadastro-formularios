# =============================================================================
# Auto Scaling de PRODUCAO — FABIANO-57
# =============================================================================
# O QUE ACONTECE NO MOMENTO DO APPLY
#
# A maquina nova assume o Elastic IP no PRIMEIRO passo do user_data, antes de
# ter containers no ar. Isso significa uma janela de indisponibilidade de 4 a 6
# minutos, entre o EIP mudar de dono e o nginx comecar a responder.
#
# Nao ha como evitar isso mantendo o mesmo endereco: ou o EIP aponta para a
# maquina velha, ou para a nova. O que existe e uma volta atras barata — uma
# unica chamada devolve o EIP para i-008f8d272588845ef, e o servico volta em
# segundos, porque a maquina velha continua de pe o tempo todo.
#
# Por isso a troca e feita de madrugada, e por isso a maquina velha NAO deve ser
# terminada nos proximos dias.
# =============================================================================
module "asg_producao" {
  source   = "./asg"
  ambiente = "producao"

  # Igual a maquina atual. Nao e hora de mudar duas variaveis ao mesmo tempo:
  # se algo degradar, queremos saber se foi o provisionamento ou o tamanho.
  instance_type = "t3.medium"

  # Duas zonas. Producao hoje vive em uma so: queda de zona derruba o servico e
  # nao ha para onde subir a substituta — o que anularia a razao de ter um ASG.
  subnet_ids = [
    "subnet-0dfc383d51ee3004a", # us-east-1a — onde producao roda hoje
    "subnet-0e267e4adcae169a8", # us-east-1b
  ]

  security_group_ids = ["sg-0a32a93ab12d715f0"] # o mesmo de producao hoje

  # O EIP de producao. Como o endereco nao muda, nenhum registro de DNS precisa
  # ser tocado e nao ha propagacao para esperar.
  eip_allocation_id = "eipalloc-025082e8787508bb8"

  # Nome proprio de IAM: a raiz ja gerencia "poc-fabiano-producao-ec2".
  sufixo_iam = "-asg"

  bucket_imagens   = "cadastro-fabiano-uploads"
  bucket_backup    = "fabiano-db-backups-135133927228"
  bucket_artefatos = "fabiano-artefatos-135133927228"

  # A mesma versao que producao roda agora e que homologacao acabou de validar.
  backend_tag = "7ea097a"
  ghcr_owner  = "fabianombs"

  # Vazio de proposito nesta primeira subida. Alarme por e-mail e uma variavel a
  # mais para dar errado durante uma troca de producao; entra como passo proprio
  # depois que a maquina estiver estavel.
  email_alertas = ""

  # Sem key pair: o acesso e por SSM Session Manager, que ja usamos a noite toda
  # e nao exige porta 22 aberta.
  key_name = "poc-fabiano-key"
}

output "asg_producao_nome" {
  description = "Nome do grupo de producao."
  value       = module.asg_producao.asg_name
}
