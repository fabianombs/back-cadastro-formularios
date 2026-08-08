output "ec2_public_ip" {
  description = "IP publico fixo da EC2 de producao (Elastic IP)"
  value       = aws_eip.app.public_ip
}

output "ec2_instance_id" {
  description = "Id da instancia de producao — util para conferir antes de comando destrutivo na CLI"
  value       = aws_instance.app.id
}

output "rds_endpoint" {
  description = "Endpoint do RDS MySQL de producao"
  value       = aws_db_instance.mysql.address
}

output "rds_port" {
  value = aws_db_instance.mysql.port
}

output "rds_engine_version" {
  description = "Versao efetiva do engine. Depois do FABIANO-9 deve virar 8.4.x aqui tambem."
  value       = aws_db_instance.mysql.engine_version_actual
}

output "rds_backup_retention" {
  description = "Dias de retencao. Se isto vier 0, o backup automatico esta desligado — e o alarme e imediato."
  value       = aws_db_instance.mysql.backup_retention_period
}

output "ssh_command" {
  value = "ssh -i ~/.ssh/poc-fabiano ec2-user@${aws_eip.app.public_ip}"
}

# O output antigo "api_url" devolvia a string fixa "https://100-30-35-83.sslip.io".
# Virou derivado do IP real: se o Elastic IP mudar, o output acompanha em vez de
# mentir. O nome sslip.io deriva do proprio endereco, entao a substituicao de
# pontos por hifens reproduz o dominio.
output "api_url_sslip" {
  description = "URL da API pelo nome derivado do IP. Some quando api.nexventa.com.br existir (FABIANO-51)."
  value       = "https://${replace(aws_eip.app.public_ip, ".", "-")}.sslip.io"
}
