# Sapphire AI Service - Deployment Guide

**Version:** 1.0  
**Target:** Ubuntu 22.04 LTS or 24.04 LTS  
**Deployment Type:** On-Premise (100% Local)

---

## Overview

Sapphire AI Service (SAIS) is an enterprise RAG knowledge search engine that runs completely on-premise. No data ever leaves your infrastructure.

**What gets installed:**
- Java 21 (Spring Boot application)
- PostgreSQL 15+ (metadata storage)
- Qdrant (vector database)
- Ollama (optional - for local LLM inference)

**Deployment options:**
- **Phase A (Recommended):** Remote LLM (Groq API) - fast, no GPU needed
- **Phase B:** Local LLM (Ollama) - requires GPU, full data sovereignty

---

## Prerequisites

### System Requirements

**Minimum (Phase A - Remote LLM):**
- Ubuntu 22.04/24.04 LTS
- 4 CPU cores
- 16 GB RAM
- 100 GB disk space
- Internet access (for Groq API)

**Recommended (Phase B - Local LLM):**
- 8+ CPU cores
- 32+ GB RAM
- 200+ GB disk space
- NVIDIA GPU with 24GB+ VRAM (for Mistral 7B)

### Network Requirements

- Outbound HTTPS (443) for:
  - Groq API (api.groq.com) - if using remote LLM
  - Confluence API (yourcompany.atlassian.net)
- Inbound ports:
  - 8080 (SAIS API)
  - 3000 (UI - optional)

---

## Installation Steps

### Step 1: Install Java 21

```bash
# Update package list
sudo apt update

# Install OpenJDK 21
sudo apt install -y openjdk-21-jdk

# Verify installation
java -version
# Should show: openjdk version "21.x.x"
```

### Step 2: Install PostgreSQL

```bash
# Install PostgreSQL 15
sudo apt install -y postgresql postgresql-contrib

# Start PostgreSQL
sudo systemctl start postgresql
sudo systemctl enable postgresql

# Create database and user
sudo -u postgres psql << SQL
CREATE DATABASE sapphireai;
CREATE USER sapphire WITH PASSWORD 'YOUR_SECURE_PASSWORD';
GRANT ALL PRIVILEGES ON DATABASE sapphireai TO sapphire;
\q
SQL

# Verify connection
psql -U sapphire -d sapphireai -h localhost -c "SELECT version();"
```

### Step 3: Install Qdrant (Vector Database)

```bash
# Pull Qdrant Docker image
docker pull qdrant/qdrant:v1.7.4

# Run Qdrant
docker run -d \
  --name qdrant \
  -p 6333:6333 -p 6334:6334 \
  -v $(pwd)/qdrant_storage:/qdrant/storage \
  --restart unless-stopped \
  qdrant/qdrant:v1.7.4

# Verify Qdrant is running
curl http://localhost:6333/healthz
# Should return: "OK"
```

### Step 4: Install Ollama (Optional - for local LLM)

**Skip this for Phase A (using Groq remote LLM)**

```bash
# Install Ollama
curl -fsSL https://ollama.com/install.sh | sh

# Pull embedding model (required)
ollama pull nomic-embed-text

# Pull LLM model (optional - for Phase B)
ollama pull mistral:7b-instruct

# Verify
curl http://localhost:11434/api/version
```

---

## Step 5: Deploy Sapphire AI Service

### 5.1: Create Application User

```bash
# Create sapphire user
sudo useradd -r -m -s /bin/bash sapphire

# Create directories
sudo mkdir -p /opt/sapphire
sudo mkdir -p /var/sapphire/documents
sudo mkdir -p /var/log/sapphire-ai

# Set ownership
sudo chown -R sapphire:sapphire /opt/sapphire
sudo chown -R sapphire:sapphire /var/sapphire
sudo chown -R sapphire:sapphire /var/log/sapphire-ai
```

### 5.2: Upload Application JAR

```bash
# Copy JAR to server
scp sapphire-ai-service-1.0.0.jar user@server:/tmp/

# Move to application directory
sudo mv /tmp/sapphire-ai-service-1.0.0.jar /opt/sapphire/
sudo chown sapphire:sapphire /opt/sapphire/sapphire-ai-service-1.0.0.jar
```

### 5.3: Create Configuration File

```bash
sudo nano /opt/sapphire/application-prod.yml
```

**Paste this configuration:**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sapphireai
    username: sapphire
    password: YOUR_SECURE_PASSWORD
    
  jpa:
    show-sql: false

server:
  port: 8080

sapphire:
  # Remote LLM (Phase A)
  inference:
    strategy: remote
    remote:
      enabled: true
      api-url: https://api.groq.com/openai
      api-key: YOUR_GROQ_API_KEY
      model: llama-3.3-70b-versatile
      endpoint: /v1/chat/completions

  # Embeddings (local - no API needed)
  embedding:
    provider: huggingface
    huggingface:
      enabled: true
      model-name: sentence-transformers/all-MiniLM-L6-v2
      dimension: 384

  vector:
    base-url: http://localhost:6334
    collection-name: sapphire_chunks

  document:
    storage-path: /var/sapphire/documents

  security:
    jwt:
      secret: GENERATE_A_LONG_RANDOM_SECRET_HERE_64_CHARS_MIN
    rate-limit:
      enabled: true
      requests-per-minute: 100

logging:
  level:
    root: WARN
    com.sapphireai: INFO
  file:
    name: /var/log/sapphire-ai/application.log
```

**Important:** Replace:
- `YOUR_SECURE_PASSWORD` - PostgreSQL password
- `YOUR_GROQ_API_KEY` - Get free key from https://console.groq.com
- `GENERATE_A_LONG_RANDOM_SECRET_HERE` - Run: `openssl rand -base64 64`

### 5.4: Create Systemd Service

```bash
sudo nano /etc/systemd/system/sapphire-ai.service
```

**Paste:**

```ini
[Unit]
Description=Sapphire AI Service
After=network.target postgresql.service docker.service

[Service]
Type=simple
User=sapphire
Group=sapphire
WorkingDirectory=/opt/sapphire
ExecStart=/usr/bin/java -Xmx4G -jar /opt/sapphire/sapphire-ai-service-1.0.0.jar --spring.config.location=file:/opt/sapphire/application-prod.yml
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=sapphire-ai

[Install]
WantedBy=multi-user.target
```

### 5.5: Start Application

```bash
# Reload systemd
sudo systemctl daemon-reload

# Enable service (start on boot)
sudo systemctl enable sapphire-ai

# Start service
sudo systemctl start sapphire-ai

# Check status
sudo systemctl status sapphire-ai

# Follow logs
sudo journalctl -u sapphire-ai -f
```

---

## Step 6: Verify Installation

### 6.1: Health Check

```bash
# Wait 30 seconds for startup, then:
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP"}
```

### 6.2: API Test

```bash
# Test authentication
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Should return: {"accessToken":"eyJ..."}
```

### 6.3: Database Check

```bash
psql -U sapphire -d sapphireai -h localhost -c "SELECT COUNT(*) FROM users;"
# Should return at least 1 (default admin user)
```

---

## Step 7: Configure Confluence Connector

### 7.1: Generate Confluence API Token

1. Go to: https://id.atlassian.com/manage-profile/security/api-tokens
2. Click "Create API token"
3. Name: "Sapphire AI Service"
4. Copy token (keep secure)

### 7.2: Add Connection via API

```bash
# Get auth token first
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.accessToken')

# Add Confluence connection
curl -X POST http://localhost:8080/api/confluence/connections \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "connectionName": "Company Confluence",
    "instanceUrl": "https://yourcompany.atlassian.net/wiki",
    "username": "your-email@company.com",
    "apiToken": "YOUR_CONFLUENCE_API_TOKEN",
    "syncEnabled": true
  }'
```

### 7.3: Trigger Initial Sync

```bash
# Get connection ID from previous response, then:
CONNECTION_ID="your-connection-id"

curl -X POST http://localhost:8080/api/confluence/connections/$CONNECTION_ID/sync/full \
  -H "Authorization: Bearer $TOKEN"

# Monitor sync progress in logs:
sudo journalctl -u sapphire-ai -f | grep -i "confluence"
```

---

## Troubleshooting

### Service Won't Start

**Check logs:**
```bash
sudo journalctl -u sapphire-ai -n 100 --no-pager
```

**Common issues:**
1. **Port 8080 already in use:**
   ```bash
   sudo lsof -i :8080
   # Kill process or change port in application-prod.yml
   ```

2. **PostgreSQL connection refused:**
   ```bash
   # Check PostgreSQL is running
   sudo systemctl status postgresql
   
   # Test connection
   psql -U sapphire -d sapphireai -h localhost
   ```

3. **Qdrant not accessible:**
   ```bash
   docker ps | grep qdrant
   curl http://localhost:6333/healthz
   ```

### Search Returns No Results

**Check Qdrant collection:**
```bash
curl http://localhost:6333/collections/sapphire_chunks
# Should show: "points_count": > 0
```

**Check documents indexed:**
```bash
psql -U sapphire -d sapphireai -h localhost -c \
  "SELECT COUNT(*) FROM documents WHERE status='COMPLETED';"
```

### High Memory Usage

**Reduce Java heap:**
```bash
# Edit service file
sudo nano /etc/systemd/system/sapphire-ai.service

# Change: -Xmx4G to -Xmx2G
# Reload and restart
sudo systemctl daemon-reload
sudo systemctl restart sapphire-ai
```

---

## Security Hardening

### 1. Change Default Admin Password

```bash
# Via API after first login
curl -X PUT http://localhost:8080/api/users/admin/password \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"newPassword":"SecurePassword123!"}'
```

### 2. Enable HTTPS (Production)

**Install nginx:**
```bash
sudo apt install -y nginx certbot python3-certbot-nginx

# Get SSL certificate
sudo certbot --nginx -d sais.yourcompany.com

# Configure nginx reverse proxy
sudo nano /etc/nginx/sites-available/sapphire-ai
```

**Nginx config:**
```nginx
server {
    listen 443 ssl;
    server_name sais.yourcompany.com;
    
    ssl_certificate /etc/letsencrypt/live/sais.yourcompany.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/sais.yourcompany.com/privkey.pem;
    
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 3. Firewall Rules

```bash
# Allow only necessary ports
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 443/tcp   # HTTPS
sudo ufw enable
```

---

## Backup & Recovery

### Backup Script

```bash
#!/bin/bash
BACKUP_DIR=/var/backups/sapphire-ai
DATE=$(date +%Y%m%d_%H%M%S)

# Backup PostgreSQL
pg_dump -U sapphire sapphireai > $BACKUP_DIR/db_$DATE.sql

# Backup Qdrant
docker exec qdrant sh -c "cd /qdrant/storage && tar czf - ." > $BACKUP_DIR/qdrant_$DATE.tar.gz

# Backup documents
tar czf $BACKUP_DIR/documents_$DATE.tar.gz /var/sapphire/documents

# Keep last 7 days
find $BACKUP_DIR -mtime +7 -delete
```

### Restore

```bash
# Restore PostgreSQL
psql -U sapphire sapphireai < db_20260201_120000.sql

# Restore Qdrant
docker exec -i qdrant sh -c "cd /qdrant/storage && tar xzf -" < qdrant_20260201_120000.tar.gz
docker restart qdrant

# Restore documents
tar xzf documents_20260201_120000.tar.gz -C /
```

---

## Monitoring

### Check Service Health

```bash
# Service status
sudo systemctl status sapphire-ai

# Recent logs
sudo journalctl -u sapphire-ai -n 50

# Error logs only
sudo journalctl -u sapphire-ai -p err

# Resource usage
ps aux | grep sapphire
```

### Metrics Endpoint

```bash
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

---

## Upgrading

### Rolling Upgrade

```bash
# 1. Backup (see above)

# 2. Download new JAR
scp sapphire-ai-service-1.1.0.jar user@server:/tmp/

# 3. Stop service
sudo systemctl stop sapphire-ai

# 4. Replace JAR
sudo mv /tmp/sapphire-ai-service-1.1.0.jar /opt/sapphire/
sudo chown sapphire:sapphire /opt/sapphire/sapphire-ai-service-1.1.0.jar

# 5. Update service file
sudo nano /etc/systemd/system/sapphire-ai.service
# Change JAR filename to 1.1.0

# 6. Start service
sudo systemctl daemon-reload
sudo systemctl start sapphire-ai

# 7. Verify
curl http://localhost:8080/actuator/health
```

---

## Support

**Documentation:** https://github.com/your-repo/sapphire-ai  
**Email:** support@yourcompany.com  
**Issues:** Create ticket with logs from `journalctl -u sapphire-ai -n 200`

---

## Appendix: Phase B (Local LLM)

**Switch to local inference after initial setup:**

```yaml
# application-prod.yml changes:
sapphire:
  inference:
    strategy: quality  # Use local GPU inference
    
    quality:
      enabled: true
      model: mistral:7b-instruct
    
    remote:
      enabled: false  # Disable Groq
```

**Requires:**
- NVIDIA GPU with 24GB+ VRAM
- CUDA drivers installed
- Ollama with mistral:7b-instruct pulled

**Restart service after config change:**
```bash
sudo systemctl restart sapphire-ai
```
