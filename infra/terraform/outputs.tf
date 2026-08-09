# =============================================================================
# Outputs
# =============================================================================
# Output e o que a proxima pessoa le as pressas, no meio de um incidente. Um
# output correto que induz a conclusao errada e pior que output nenhum.
#
# Correcao de 09/08/2026: depois da virada (FABIANO-47), 'ec2_instance_id' e
# 'ec2_public_ip' passaram a se referir a MAQUINAS DIFERENTES — o id vem do
# recurso (maquina antiga), o IP vem do Elastic IP (que esta na nova). Cada um
# certo isolado, e juntos contando uma mentira. Renomeados para dizer de qual
# maquina falam.
# =============================================================================

output "eip_producao" {
  description = "Elastic IP que atende producao. Desde 08/08/2026 aponta para a maquina NOVA (i-008f8d272588845ef), que nao e gerenciada por este state."
  value       = aws_eip.app.public_ip
}

output "ec2_antiga_instance_id" {
  description = "Id da EC2 ANTIGA (t2.micro, AL2, JAR+systemd). Mantida ligada ate 18/08/2026 como rollback — FABIANO-48. NAO e quem atende o cliente."
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
  description = "Versao efetiva do engine, lida da AWS. 8.4.10 desde o switchover Blue/Green de 08/08/2026 (FABIANO-9)."
  value       = aws_db_instance.mysql.engine_version_actual
}

output "rds_backup_retention" {
  description = "Dias de retencao. Se isto vier 0, o backup automatico esta desligado — e o alarme e imediato."
  value       = aws_db_instance.mysql.backup_retention_period
}

# O ProxyJump e obrigatorio para a maquina antiga: ela perdeu o IP publico
# quando o EIP migrou, e so e alcancavel de dentro da VPC.
output "ssh_maquina_nova" {
  description = "Acesso a maquina que atende producao hoje."
  value       = "ssh -i ~/.ssh/poc-fabiano ec2-user@${aws_eip.app.public_ip}"
}

output "ssh_maquina_antiga" {
  description = "Acesso a maquina legada, saltando pela nova. Sem IP publico."
  value       = "ssh -i ~/.ssh/poc-fabiano -J ec2-user@${aws_eip.app.public_ip} ec2-user@${aws_instance.app.private_ip}"
}

output "api_url" {
  description = "URL real da API desde 08/08/2026 (FABIANO-51). O certificado e emitido para este nome."
  value       = "https://api.nexventa.com.br"
}

# Mantido como rede de seguranca: o bloco do nginx para este nome continua no ar
# de proposito, para o caso de o dominio proprio ter problema de DNS ou de
# certificado. Sai quando o api.nexventa.com.br tiver algumas semanas de estrada.
output "api_url_sslip_fallback" {
  description = "Nome derivado do proprio IP. Continua respondendo, mas NAO e o endereco de producao."
  value       = "https://${replace(aws_eip.app.public_ip, ".", "-")}.sslip.io"
}
