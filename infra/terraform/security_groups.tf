resource "aws_security_group" "ec2" {
  name        = "${var.project_name}-ec2-sg"
  description = "Security group para EC2 - SSH, HTTP e HTTPS"

  # ATENCAO: ssh_allowed_cidr tem default 0.0.0.0/0, e e assim que esta na AWS
  # hoje. Com passwordauthentication=no na EC2 a forca bruta nao funciona, mas
  # a superficie existe — ha 70 tentativas registradas em /var/log/secure.
  # Restringir exige combinar quais IPs precisam entrar (FABIANO-45).
  ingress {
    description = "SSH restrito"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
  }

  ingress {
    description = "HTTP (necessario para certificado SSL Certbot)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # A porta 8080 NAO e exposta. Existia uma regra liberando 8080 para
  # 0.0.0.0/0 "apenas para debug" — ela deixava a aplicacao acessivel em HTTP
  # puro pelo IP, contornando o nginx e o certificado: login trafegando em
  # texto claro. Removida da AWS e daqui em 04/08/2026 (FABIANO-45).
  #
  # O nginx alcanca a aplicacao por localhost:8080, dentro da propria
  # instancia, e security group nao filtra loopback — nada quebra sem esta
  # regra.

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-ec2-sg"
  }
}

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-sg"
  description = "Security group para RDS MySQL - acessivel apenas pela EC2"

  ingress {
    description     = "MySQL apenas da EC2"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-rds-sg"
  }
}
