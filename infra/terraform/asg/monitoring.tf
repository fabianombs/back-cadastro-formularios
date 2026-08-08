# =============================================================================
# Alarmes — o vigia que NAO mora na maquina vigiada
# =============================================================================
# Este arquivo e a resposta ao furo que a stack de observabilidade nao cobre:
# Prometheus, Loki e Grafana rodam DENTRO da EC2. Se ela morrer, morrem juntos e
# nenhum alerta sai. O monitoramento vigia tudo, menos a propria morte.
#
# CloudWatch roda na AWS, nao na instancia. Alarme daqui dispara mesmo com a
# maquina desligada — que e exatamente quando importa.

resource "aws_sns_topic" "alertas" {
  name         = "${var.project_name}-${var.ambiente}-alertas"
  display_name = "Fabiano ${var.ambiente}"
  tags         = { Ambiente = var.ambiente }
}

# A assinatura por e-mail exige CONFIRMACAO: a AWS manda uma mensagem com link e
# o alarme so entrega depois do clique. Enquanto ninguem confirmar, o alarme
# dispara e nao avisa — mesmo modo de falha do contact point do Grafana.
# Conferir em: SNS > Subscriptions > Status = Confirmed.
resource "aws_sns_topic_subscription" "email" {
  count     = var.email_alertas != "" ? 1 : 0
  topic_arn = aws_sns_topic.alertas.arn
  protocol  = "email"
  endpoint  = var.email_alertas
}

# -----------------------------------------------------------------------------
# Nao ha instancia em servico
# -----------------------------------------------------------------------------
# O alarme mais importante do arquivo. Se o ASG nao conseguir manter uma
# instancia de pe — falta de capacidade na zona, user_data quebrado, AMI
# indisponivel — ele tenta em silencio para sempre. Isto e o que quebra o
# silencio.
resource "aws_cloudwatch_metric_alarm" "sem_instancia" {
  alarm_name          = "${var.project_name}-${var.ambiente}-sem-instancia"
  alarm_description   = "O Auto Scaling nao tem nenhuma instancia em servico. A aplicacao esta fora do ar."
  namespace           = "AWS/AutoScaling"
  metric_name         = "GroupInServiceInstances"
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 3
  threshold           = 1
  comparison_operator = "LessThanThreshold"

  # Sem dado tambem dispara: metrica ausente e indistinguivel de grupo vazio, e
  # nas duas hipoteses alguem precisa olhar.
  treat_missing_data = "breaching"

  dimensions = { AutoScalingGroupName = aws_autoscaling_group.app.name }

  alarm_actions = [aws_sns_topic.alertas.arn]
  ok_actions    = [aws_sns_topic.alertas.arn]
}

# -----------------------------------------------------------------------------
# Falha de verificacao de status
# -----------------------------------------------------------------------------
# Redundante com a acao do proprio ASG, de proposito: o ASG substitui a
# instancia e resolve o problema, mas ninguem fica sabendo que aconteceu.
# Failover silencioso transforma uma falha visivel em duas invisiveis — a
# segunda sendo descoberta quando a substituta tambem cair.
resource "aws_cloudwatch_metric_alarm" "status_check" {
  alarm_name          = "${var.project_name}-${var.ambiente}-status-check"
  alarm_description   = "Verificacao de status da instancia falhou. O ASG deve substitui-la; este alarme existe para que a substituicao nao passe despercebida."
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = { AutoScalingGroupName = aws_autoscaling_group.app.name }

  alarm_actions = [aws_sns_topic.alertas.arn]
  ok_actions    = [aws_sns_topic.alertas.arn]
}

# -----------------------------------------------------------------------------
# CPU estrangulada por falta de credito
# -----------------------------------------------------------------------------
# So faz sentido em instancia burstavel. Com cpu_credits = "unlimited" o
# estrangulamento nao acontece, mas o saldo negativo vira custo — e e melhor
# saber antes da fatura.
resource "aws_cloudwatch_metric_alarm" "creditos_cpu" {
  count = var.cpu_credits == "unlimited" ? 1 : 0

  alarm_name          = "${var.project_name}-${var.ambiente}-creditos-cpu"
  alarm_description   = "A instancia esta consumindo credito de CPU excedente ha uma hora. Custo extra em curso, e sinal de que o tipo da instancia ficou pequeno."
  namespace           = "AWS/EC2"
  metric_name         = "CPUSurplusCreditBalance"
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 12
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = { AutoScalingGroupName = aws_autoscaling_group.app.name }

  alarm_actions = [aws_sns_topic.alertas.arn]
}
