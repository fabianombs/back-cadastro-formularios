output "ec2_public_ip" {
  description = "IP publico fixo da EC2 (Elastic IP)"
  value       = aws_eip.app.public_ip
}

output "rds_endpoint" {
  description = "Endpoint do RDS MySQL"
  value       = aws_db_instance.mysql.address
}

output "rds_port" {
  description = "Porta do RDS MySQL"
  value       = aws_db_instance.mysql.port
}

output "ssh_command" {
  description = "Comando para conectar na EC2 via SSH"
  value       = "ssh -i ~/.ssh/poc-fabiano ec2-user@${aws_eip.app.public_ip}"
}

output "api_url" {
  description = "URL base da API Spring Boot via HTTPS"
  value       = "https://100-30-35-83.sslip.io"
}
