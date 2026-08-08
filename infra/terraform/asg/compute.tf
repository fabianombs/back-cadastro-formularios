# =============================================================================
# Launch template + Auto Scaling Group
# =============================================================================
# min = max = desired = 1. Isto NAO e escala horizontal — e auto-recuperacao.
# A aplicacao nao roda em duas instancias hoje (ver variavel usar_alb); o que o
# grupo entrega e: instancia morreu, outra nasce igual, com o mesmo endereco.

resource "aws_launch_template" "app" {
  name_prefix   = "${var.project_name}-${var.ambiente}-"
  image_id      = data.aws_ami.al2023.id
  instance_type = var.instance_type
  key_name      = var.key_name != "" ? var.key_name : null

  vpc_security_group_ids = var.security_group_ids

  iam_instance_profile {
    arn = aws_iam_instance_profile.instancia.arn
  }

  credit_specification {
    cpu_credits = var.cpu_credits
  }

  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      volume_size = var.volume_size
      # gp3 e nao gp2: mesma faixa de preco, e a linha de base de IOPS e
      # throughput deixa de depender do tamanho do disco. Num disco de 20 GB o
      # gp2 entrega 100 IOPS; o gp3 entrega 3000. Ver FABIANO-42, que fez a
      # mesma troca no RDS — la a premissa de ganho foi derrubada pela medicao,
      # aqui e higiene barata do mesmo jeito.
      volume_type           = "gp3"
      encrypted             = true
      delete_on_termination = true
    }
  }

  metadata_options {
    # IMDSv2 obrigatorio. Com o v1, uma falha de SSRF na aplicacao permitiria
    # ler as credenciais da role pela URL de metadados. Exigir token elimina a
    # classe inteira de ataque, e nao custa nada.
    http_tokens                 = "required"
    http_endpoint               = "enabled"
    http_put_response_hop_limit = 2 # 2 para o metadado alcancar dentro de container
  }

  monitoring {
    # Metrica de 1 minuto em vez de 5. Custa poucos centavos e e a diferenca
    # entre perceber um pico e ve-lo diluido na media.
    enabled = true
  }

  user_data = base64encode(templatefile("${path.module}/templates/user_data.sh.tftpl", {
    ambiente          = var.ambiente
    project_name      = var.project_name
    regiao            = data.aws_region.atual.name
    eip_allocation_id = var.eip_allocation_id
    bucket_artefatos  = var.bucket_artefatos
    bucket_backup     = var.bucket_backup
    backend_tag       = var.backend_tag
    ghcr_owner        = var.ghcr_owner
  }))

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name       = "${var.project_name}-${var.ambiente}"
      Projeto    = var.project_name
      Ambiente   = var.ambiente
      Gerenciado = "terraform"
    }
  }

  tag_specifications {
    resource_type = "volume"
    tags = {
      Name     = "${var.project_name}-${var.ambiente}-raiz"
      Ambiente = var.ambiente
    }
  }

  lifecycle {
    # O template e imutavel na pratica: mudou, nasce versao nova. Criar antes de
    # destruir evita a janela em que o ASG nao tem template para usar.
    create_before_destroy = true
  }
}

resource "aws_autoscaling_group" "app" {
  name_prefix         = "${var.project_name}-${var.ambiente}-"
  vpc_zone_identifier = var.subnet_ids

  min_size         = 1
  max_size         = 1
  desired_capacity = 1

  launch_template {
    id      = aws_launch_template.app.id
    version = "$Latest"
  }

  # -----------------------------------------------------------------------------
  # Saude: EC2 enquanto nao houver ALB — e e importante saber o que isso NAO cobre
  # -----------------------------------------------------------------------------
  # Com health_check_type = "EC2", o ASG so enxerga saude de INSTANCIA: hardware,
  # rede, sistema travado. Maquina de pe com a aplicacao morta passa por saudavel.
  #
  # Cobrir isso exige ou um ALB (variavel usar_alb) ou um alarme externo chamando
  # set-instance-health. Enquanto nao houver, o alarme de HTTP externo e quem
  # percebe aplicacao morta — e ele vive fora da maquina, que e o ponto.
  health_check_type         = var.usar_alb ? "ELB" : "EC2"
  health_check_grace_period = 300 # o boot completo leva ~5 min: pacotes, imagem, certificado

  # Termina a instancia antiga so depois que a nova estiver saudavel. Com uma
  # instancia so, isso significa uma janela curta de duas maquinas — e o Elastic
  # IP fica com quem o associar por ultimo, ou seja, a nova.
  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 0
      instance_warmup        = 300
    }
    triggers = ["launch_template"]
  }

  tag {
    key                 = "Name"
    value               = "${var.project_name}-${var.ambiente}"
    propagate_at_launch = true
  }
  tag {
    key                 = "Ambiente"
    value               = var.ambiente
    propagate_at_launch = true
  }
  tag {
    key                 = "Gerenciado"
    value               = "terraform"
    propagate_at_launch = true
  }

  lifecycle {
    create_before_destroy = true

    # Trava contra um pe na jaca silencioso: usar_alb = true troca a saude para
    # "ELB", mas o ALB e o target group AINDA NAO EXISTEM neste modulo. Sem esta
    # precondicao, o ASG passaria a consultar um balanceador inexistente,
    # marcaria a instancia como insalubre e ficaria destruindo e recriando
    # maquina em laco — e o `apply` teria dado tudo verde.
    precondition {
      condition     = var.usar_alb == false
      error_message = "usar_alb = true ainda nao e suportado: o ALB e o target group nao foram escritos. Antes disso e preciso resolver os anexos no disco (FABIANO-19), senao uma segunda instancia serve link quebrado."
    }

    # Decisao consciente: desired_capacity NAO e ignorado. Se alguem ajustar o
    # valor pelo console para depurar, o proximo apply devolve ao que esta no
    # codigo — e e assim que se quer. O risco oposto (codigo e console
    # discordando em silencio) e pior.
    #
    # ignore_changes = [desired_capacity]
  }

  timeouts {
    delete = "20m"
  }
}
