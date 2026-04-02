#!/bin/bash
# ─────────────────────────────────────────────────────────────────
# Deploy Script — URL Shortener
# Usage:
#   chmod +x deploy.sh
#   ./deploy.sh          → build + run locally
#   ./deploy.sh prod     → build + run with prod profile
# ─────────────────────────────────────────────────────────────────

set -e   # exit immediately on any error

PROFILE=${1:-local}
JAR="target/url-shortener-1.0.0.jar"
APP_PORT=8080

echo ""
echo "════════════════════════════════════════════"
echo "  URL Shortener — Deploy Script"
echo "  Profile: $PROFILE"
echo "════════════════════════════════════════════"
echo ""

# ── Step 1: Start local infrastructure (local mode only) ────────
if [ "$PROFILE" = "local" ]; then
    echo "▶ Starting MySQL + Redis via Docker Compose..."
    docker-compose up -d
    echo "⏳ Waiting for MySQL to be ready..."
    sleep 8
fi

# ── Step 2: Build the JAR ────────────────────────────────────────
echo ""
echo "▶ Building application with Maven..."
./mvnw clean package -DskipTests || mvn clean package -DskipTests

echo ""
echo "✅ Build complete: $JAR"

# ── Step 3: Stop any running instance ────────────────────────────
echo ""
echo "▶ Stopping any existing instance on port $APP_PORT..."
lsof -ti tcp:$APP_PORT | xargs kill -9 2>/dev/null || true

# ── Step 4: Run the app ──────────────────────────────────────────
echo ""
echo "▶ Starting application [profile=$PROFILE]..."
java -jar "$JAR" \
     --spring.profiles.active=$PROFILE \
     --server.port=$APP_PORT &

echo ""
echo "⏳ Waiting for startup..."
sleep 6

# ── Step 5: Health check ─────────────────────────────────────────
echo ""
echo "▶ Running health check..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$APP_PORT/api/health)

if [ "$HTTP_CODE" = "200" ]; then
    echo ""
    echo "════════════════════════════════════════════"
    echo "  ✅ App is LIVE at http://localhost:$APP_PORT"
    echo "════════════════════════════════════════════"
else
    echo ""
    echo "❌ Health check failed (HTTP $HTTP_CODE)"
    echo "   Check logs above for errors."
    exit 1
fi
