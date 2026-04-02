# Deployment Guide — URL Shortener

Two paths: **Railway (free, zero cost)** and **AWS (production-grade)**.
Both use the same codebase — only environment variables differ.

---

## Path A — Railway + Upstash + PlanetScale (Zero Cost ✅)

This mirrors the full AWS architecture at zero cost. Perfect for college demos.

### Step 1 — Prepare Accounts (all free)

| Service | URL | What You Get Free |
|---|---|---|
| Railway | railway.app | 500 hrs/month compute, auto-deploy from GitHub |
| Upstash | upstash.com | Redis, 10,000 req/day |
| PlanetScale | planetscale.com | MySQL, 5GB storage, 1 billion row reads/month |

---

### Step 2 — Push to GitHub

```bash
cd url-shortener-complete

git init
git add .
git commit -m "feat: initial URL shortener implementation"

# Create a repo on github.com, then:
git remote add origin https://github.com/YOUR_USERNAME/url-shortener.git
git push -u origin main
```

---

### Step 3 — Setup PlanetScale (MySQL)

```bash
# 1. Go to planetscale.com → Create account → New database
#    Name: url-shortener | Region: ap-south-1 (Mumbai) for India

# 2. Click "Connect" → Connect with: Java / JDBC
#    Copy the connection string — looks like:
#    jdbc:mysql://aws.connect.psdb.cloud/url-shortener?sslMode=VERIFY_IDENTITY

# 3. Create a password — save the username + password
#    (PlanetScale uses branch-based schemas — your tables auto-create via JPA)
```

---

### Step 4 — Setup Upstash (Redis)

```bash
# 1. Go to upstash.com → Create account → Create Database
#    Name: url-shortener-cache
#    Region: ap-south-1 (Mumbai)
#    Type: Regional (free)
#    TLS: ON (required)

# 2. From dashboard copy:
#    Endpoint:  caring-cat-12345.upstash.io
#    Port:      6379
#    Password:  AXXXXabcdef...
```

---

### Step 5 — Deploy Backend on Railway

```bash
# Option A: Via CLI
npm install -g @railway/cli
railway login
railway init       # select "Empty Project"
railway link       # link to your GitHub repo
railway up         # deploy

# Option B: Via Dashboard (easier)
# 1. railway.app → New Project → Deploy from GitHub repo
# 2. Select your repo → Railway auto-detects Maven and builds the JAR
```

**Set Environment Variables in Railway dashboard → Variables tab:**

```
SPRING_PROFILES_ACTIVE    = prod
DATABASE_URL              = jdbc:mysql://aws.connect.psdb.cloud/url-shortener?sslMode=VERIFY_IDENTITY
DATABASE_USERNAME         = your_planetscale_username
DATABASE_PASSWORD         = your_planetscale_password
REDIS_HOST                = caring-cat-12345.upstash.io
REDIS_PORT                = 6379
REDIS_PASSWORD            = your_upstash_password
APP_BASE_URL              = https://your-app.railway.app
CACHE_TTL_HOURS           = 24
RATE_LIMIT_MAX            = 10
RATE_LIMIT_WINDOW         = 1
```

> Railway auto-assigns a public URL like `https://url-shortener-production.up.railway.app`

---

### Step 6 — Deploy Frontend on Railway (or Vercel)

**Option A — Vercel (recommended for React, completely free):**
```bash
npm install -g vercel
cd frontend
vercel

# Set environment variable:
# REACT_APP_API_URL = https://your-backend.railway.app
```

**Option B — Railway (same project):**
```bash
# Add a second service in the same Railway project
# Root: /frontend
# Build: npm run build
# Start: npx serve -s build
```

---

### Step 7 — Verify Everything Works

```bash
# 1. Health check
curl https://your-app.railway.app/api/health

# 2. Create a short URL
curl -X POST https://your-app.railway.app/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://www.google.com"}'

# 3. Test redirect (should return 302 with Location header)
curl -I https://your-app.railway.app/aB3xYz

# 4. Check analytics
curl https://your-app.railway.app/api/stats/aB3xYz

# 5. Actuator health (shows DB + Redis status)
curl https://your-app.railway.app/actuator/health
```

**Expected output from actuator/health:**
```json
{
  "status": "UP",
  "components": {
    "db":    { "status": "UP" },
    "redis": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

---

---

## Path B — AWS Deployment (Production-Grade)

Use this when you want to mention AWS on your resume.
Free tier is available for 12 months on a new AWS account.

### Architecture on AWS

```
Internet
    │
    ▼
[ Elastic IP ]
    │
    ▼
[ EC2 t2.micro — Ubuntu 22.04 ]   ← free tier: 750 hrs/month
    │   Spring Boot :8080
    │   Nginx :80 (reverse proxy)
    │
    ├──► [ RDS db.t3.micro — MySQL 8 ]   ← free tier: 750 hrs/month
    │
    └──► [ ElastiCache cache.t3.micro ]  ← NOT free ($0.017/hr)
          OR use Upstash free tier instead
```

---

### Step 1 — Launch EC2 Instance

```bash
# In AWS Console → EC2 → Launch Instance:
# AMI:          Ubuntu Server 22.04 LTS
# Instance:     t2.micro (free tier)
# Storage:      8 GB gp2
# Security Group inbound rules:
#   SSH   TCP 22    My IP
#   HTTP  TCP 80    0.0.0.0/0
#   App   TCP 8080  0.0.0.0/0  (can remove after Nginx setup)

# Download your .pem key file — keep it safe
```

---

### Step 2 — Setup EC2

```bash
# SSH into EC2
chmod 400 your-key.pem
ssh -i your-key.pem ubuntu@YOUR_EC2_PUBLIC_IP

# Update and install Java
sudo apt update && sudo apt upgrade -y
sudo apt install -y openjdk-17-jdk maven git

# Verify
java -version   # should show: openjdk 17...
```

---

### Step 3 — Create RDS MySQL Instance

```bash
# In AWS Console → RDS → Create database:
# Engine:          MySQL 8.0
# Template:        Free tier
# Instance class:  db.t3.micro
# DB name:         url_shortener
# Username:        admin
# Password:        (set a strong one)
# Public access:   YES (for initial setup)
# VPC Security Group: allow inbound 3306 from EC2 security group

# Note the endpoint: url-shortener.xxxx.us-east-1.rds.amazonaws.com
```

---

### Step 4 — Setup Nginx on EC2

```bash
sudo apt install -y nginx

# Copy the nginx.conf from this project
sudo nano /etc/nginx/sites-available/url-shortener
# (paste contents of nginx.conf)

# Enable the site
sudo ln -s /etc/nginx/sites-available/url-shortener \
           /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default   # remove default

# Test and reload
sudo nginx -t
sudo systemctl reload nginx
sudo systemctl enable nginx
```

---

### Step 5 — Build and Deploy JAR

```bash
# Option A: Build on EC2
git clone https://github.com/YOUR_USERNAME/url-shortener.git
cd url-shortener
mvn clean package -DskipTests

# Option B: Build locally, upload JAR
mvn clean package -DskipTests
scp -i your-key.pem \
    target/url-shortener-1.0.0.jar \
    ubuntu@YOUR_EC2_IP:~/
```

---

### Step 6 — Create systemd Service (Auto-Restart)

```bash
sudo nano /etc/systemd/system/url-shortener.service
```

Paste this content:
```ini
[Unit]
Description=URL Shortener Spring Boot App
After=network.target

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu
ExecStart=/usr/bin/java \
  -jar /home/ubuntu/url-shortener-1.0.0.jar \
  --spring.profiles.active=prod
SuccessExitStatus=143
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
Environment="DATABASE_URL=jdbc:mysql://YOUR_RDS_ENDPOINT:3306/url_shortener"
Environment="DATABASE_USERNAME=admin"
Environment="DATABASE_PASSWORD=YOUR_RDS_PASSWORD"
Environment="REDIS_HOST=YOUR_REDIS_HOST"
Environment="REDIS_PORT=6379"
Environment="REDIS_PASSWORD=YOUR_REDIS_PASSWORD"
Environment="APP_BASE_URL=http://YOUR_EC2_PUBLIC_IP"

[Install]
WantedBy=multi-user.target
```

```bash
# Start and enable
sudo systemctl daemon-reload
sudo systemctl start url-shortener
sudo systemctl enable url-shortener   # auto-start on reboot

# Check status
sudo systemctl status url-shortener
sudo journalctl -u url-shortener -f   # live logs
```

---

### Step 7 — Redis Options on AWS

**Free option — Upstash (recommended for college project):**
```
REDIS_HOST     = your-endpoint.upstash.io
REDIS_PORT     = 6379
REDIS_PASSWORD = your_upstash_password
```
Update `application-prod.yml` — `ssl.enabled: true` is already set.

**Paid option — ElastiCache (~$12/month, avoid for college):**
```bash
# AWS Console → ElastiCache → Create cluster
# Engine: Redis 7.x | Node: cache.t3.micro
# Same VPC as EC2
```

---

### Step 8 — Assign Elastic IP

```bash
# AWS Console → EC2 → Elastic IPs → Allocate
# Associate with your EC2 instance
# This gives you a stable IP that survives reboots

# Your app is now accessible at:
# http://YOUR_ELASTIC_IP/api/shorten
```

---

### Step 9 — Final Production Checklist

```bash
# ✅ Health check
curl http://YOUR_IP/api/health

# ✅ Actuator (DB + Redis both UP)
curl http://YOUR_IP/actuator/health

# ✅ Create short URL
curl -X POST http://YOUR_IP/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://github.com"}'

# ✅ Test redirect
curl -I http://YOUR_IP/aB3xYz

# ✅ Nginx logs
sudo tail -f /var/log/nginx/url-shortener-access.log

# ✅ App logs
sudo journalctl -u url-shortener -f
```

---

## Local Development (Fastest Setup)

```bash
# 1. Start MySQL + Redis
docker-compose up -d

# 2. Run the app
./mvnw spring-boot:run

# 3. Test
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://google.com"}'

# 4. Run frontend
cd frontend
npm install && npm start
# Opens http://localhost:3000
```

---

## Common Issues & Fixes

| Problem | Fix |
|---|---|
| `Connection refused` to MySQL | Check RDS security group allows inbound 3306 from EC2 |
| Redis SSL error on Upstash | Ensure `ssl.enabled: true` in application-prod.yml |
| App starts but 502 on Nginx | Check Spring Boot is running on port 8080 |
| `Table 'urls' doesn't exist` | JPA `ddl-auto: update` should create it on first start |
| PlanetScale `SSL required` | Add `?sslMode=VERIFY_IDENTITY` to the JDBC URL |
| Railway build fails | Ensure `pom.xml` is in the root directory |
