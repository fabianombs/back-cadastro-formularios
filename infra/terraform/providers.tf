provider "aws" {
  region = var.aws_region

  # ---------------------------------------------------------------------------
  # default_tags fica DESLIGADO de proposito ate o primeiro plan limpo
  # ---------------------------------------------------------------------------
  # Marcar todo recurso com tags padrao e boa pratica, mas ligar isso agora faria
  # o plan propor alteracao em TODOS os recursos importados — e o criterio de
  # aceite do FABIANO-10 e chegar em "No changes".
  #
  # Sequencia certa: importar, provar que o plan esta limpo, e so entao ligar as
  # tags num commit separado, cujo plan mostra apenas as tags. Assim, se aparecer
  # qualquer outra coisa no diff, e ruido de verdade e nao se perde no meio.
  #
  # default_tags {
  #   tags = {
  #     Projeto     = var.project_name
  #     Ambiente    = var.ambiente
  #     Gerenciado  = "terraform"
  #     Repositorio = "back-cadastro-formularios"
  #   }
  # }
}
