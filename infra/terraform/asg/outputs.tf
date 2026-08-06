output "asg_name" {
  value = aws_autoscaling_group.app.name
}

output "launch_template_id" {
  value = aws_launch_template.app.id
}

output "role_arn" {
  description = "ARN da role da instancia. E este ARN que precisa aparecer no bucket policy do backup, se um dia houver policy de bucket."
  value       = aws_iam_role.instancia.arn
}

output "instance_profile_name" {
  value = aws_iam_instance_profile.instancia.name
}

output "sns_topic_arn" {
  description = "Topico dos alarmes. A assinatura por e-mail precisa ser CONFIRMADA no link que a AWS envia — sem isso o alarme dispara e nao avisa."
  value       = aws_sns_topic.alertas.arn
}

output "ami_em_uso" {
  description = "AMI que o launch template resolveu neste plan. Muda sozinha quando a Amazon publica uma nova — instancia so nasce com ela na proxima substituicao."
  value       = data.aws_ami.al2023.id
}
