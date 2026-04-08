#!/bin/bash
# Script executado automaticamente na primeira inicializacao da EC2 (Amazon Linux 2)

set -e
exec > /var/log/user-data.log 2>&1

echo "=== Iniciando setup poc-fabiano ==="

# ===============================
# JAVA 21 (via Corretto repo)
# ===============================
echo "Instalando Java 21..."
rpm --import https://yum.corretto.aws/corretto.key
curl -L -o /etc/yum.repos.d/corretto.repo https://yum.corretto.aws/corretto.repo
yum install -y java-21-amazon-corretto-devel

java -version

# ===============================
# DIRETORIO DA APLICACAO
# ===============================
echo "Criando estrutura de diretorios..."
mkdir -p /app/uploads
useradd -r -s /bin/false appuser 2>/dev/null || true
chown -R appuser:appuser /app
chmod 755 /app
chmod 775 /app/uploads

# ===============================
# ARQUIVO DE VARIAVEIS DE AMBIENTE
# Preencher via SSH apos o primeiro boot
# ===============================
cat > /etc/poc-fabiano.env << 'ENVEOF'
SPRING_PROFILES_ACTIVE=prod
DB_HOST=PLACEHOLDER_RDS_ENDPOINT
DB_PORT=3306
DB_NAME=cadastro_fabiano
DB_USER=PLACEHOLDER_DB_USER
DB_PASSWORD=PLACEHOLDER_DB_PASSWORD
JWT_SECRET=PLACEHOLDER_JWT_SECRET_MIN_32_CHARS
JWT_EXPIRATION=86400000
CORS_ALLOWED_ORIGINS=https://app-forms-clients.vercel.app
UPLOAD_DIR=/app/uploads
APP_BASE_URL=https://100-30-35-83.sslip.io
ENVEOF

chmod 600 /etc/poc-fabiano.env

# ===============================
# SERVICO SYSTEMD
# ===============================
cat > /etc/systemd/system/poc-fabiano.service << 'SERVICEEOF'
[Unit]
Description=poc-fabiano Spring Boot Application
After=network.target

[Service]
User=appuser
Group=appuser
ExecStart=/usr/bin/java -jar /app/demo-0.0.1-SNAPSHOT.jar
EnvironmentFile=/etc/poc-fabiano.env
WorkingDirectory=/app
Restart=on-failure
RestartSec=15
StandardOutput=journal
StandardError=journal
SyslogIdentifier=poc-fabiano

[Install]
WantedBy=multi-user.target
SERVICEEOF

systemctl daemon-reload
systemctl enable poc-fabiano

echo "=== Setup concluido! ==="
