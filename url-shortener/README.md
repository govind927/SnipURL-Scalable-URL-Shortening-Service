# URL Shortener Service

A high-performance, production-grade URL shortening service built with Java and Spring Boot.
Converts long URLs into compact, shareable links with sub-millisecond redirect latency via Redis caching.

---

## Architecture

```
Client (Browser / Postman)
         │
         ▼
   [ Nginx / Proxy ]          ← Reverse proxy, rate limiting, SSL
         │
         ▼
  [ Spring Boot API ]         ← Stateless, horizontally scalable
         │
    ┌────┴────┐
    ▼         ▼
 [Redis]   [MySQL]            ← Cache-aside: Redis first, DB fallback
    │         │
    └────┬────┘
         ▼
  [ Analytics Logs ]          ← Event-level click tracking
```

**Cache-Aside Flow (Redirect):**
```
GET /{shortCode}
    │
    ├─► Redis.get("url:{code}")
    │       │
    │    HIT ──► Return URL immediately (< 1ms)
    │       │
    │    MISS──► MySQL.findByShortCode()
    │              │
    │              ├─► Redis.set("url:{code}", ttl=24h)
    │              └─► Return URL + Log access event
```

---

## Tech Stack

| Layer          | Technology              | Free-Tier Alternative     |
|----------------|-------------------------|---------------------------|
| Backend        | Java 17 + Spring Boot 3 | —                         |
| Database       | MySQL 8                 | PlanetScale / Railway MySQL|
| Cache          | Redis 7                 | Upstash (free 10K req/day)|
| Reverse Proxy  | Nginx                   | Built-in on Railway/Render|
| Cloud Compute  | AWS EC2                 | Railway / Render / Fly.io |
| Monitoring     | Spring Actuator         | —                         |
| Local Dev      | Docker Compose          | —                         |

---

## API Reference

### Create Short URL
```
POST /api/shorten
Content-Type: application/json

{
  "longUrl":     "https://example.com/very/long/url",
  "customAlias": "my-link",         // optional
  "expiryTime":  "2025-12-31T23:59" // optional — null = never expires
}
```
**Response 201:**
```json
{
  "shortCode": "aB3xYz",
  "shortUrl":  "http://localhost:8080/aB3xYz",
  "longUrl":   "https://example.com/very/long/url",
  "createdAt": "2024-03-15T10:30:00",
  "expiryTime": null,
  "message":   "Short URL created successfully"
}
```

---

### Redirect
```
GET /{shortCode}
```
**Response:** `302 Found` → redirects to original URL

- Uses **HTTP 302** (not 301) — prevents browser caching so every click is tracked
- Returns `410 Gone` for expired links
- Returns `404 Not Found` for invalid codes

---

### Analytics
```
GET /api/stats/{shortCode}
```
**Response 200:**
```json
{
  "shortCode":   "aB3xYz",
  "shortUrl":    "http://localhost:8080/aB3xYz",
  "longUrl":     "https://example.com/...",
  "clickCount":  142,
  "createdAt":   "2024-03-15T10:30:00",
  "expiryTime":  null,
  "isActive":    true,
  "clicksByDay": [{ "date": "2024-03-15", "clicks": 42 }],
  "clicksByCountry": [{ "country": "IN", "clicks": 80 }],
  "recentClicks": [{ "ipAddress": "192.168.1.***", "accessedAt": "..." }]
}
```

---

### Delete (Soft)
```
DELETE /api/{shortCode}
```
**Response:** `204 No Content`

Soft-deletes the link (preserves analytics history). Evicts from Redis cache.

---

## Quick Start

### Local Development (Docker — Zero Cost)

```bash
# 1. Clone and enter project
git clone <repo-url>
cd url-shortener

# 2. Start MySQL + Redis
docker-compose up -d

# 3. Run the application
./mvnw spring-boot:run

# 4. Test it
curl -X POST http://localhost:8080/api/shorten \
  -H "Content-Type: application/json" \
  -d '{"longUrl": "https://www.google.com"}'
```

---

## Free-Tier Deployment (Zero Cost — Railway)

```bash
# 1. Install Railway CLI
npm install -g @railway/cli

# 2. Login and deploy
railway login
railway init
railway up

# 3. Add MySQL plugin in Railway dashboard
# 4. Add Upstash Redis (upstash.com — free 10K req/day)

# 5. Set environment variables in Railway dashboard:
#    DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD
#    REDIS_HOST, REDIS_PORT, REDIS_PASSWORD
#    APP_BASE_URL, SPRING_PROFILES_ACTIVE=prod
```

---

## AWS Deployment (For Production Presentation)

```bash
# On EC2 instance (Ubuntu 22.04):

# 1. Install Java
sudo apt update && sudo apt install -y openjdk-17-jdk

# 2. Upload JAR
scp -i key.pem target/url-shortener-1.0.0.jar ubuntu@<EC2-IP>:~/

# 3. Install + configure Nginx
sudo apt install -y nginx
sudo cp nginx.conf /etc/nginx/sites-available/url-shortener
sudo ln -s /etc/nginx/sites-available/url-shortener /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# 4. Run app (production profile)
java -jar url-shortener-1.0.0.jar --spring.profiles.active=prod &

# 5. RDS: point DATABASE_URL to your RDS endpoint
# 6. Upstash / ElastiCache: point REDIS_HOST accordingly
```

---

## Key Design Decisions

### Why Base62 over random/hash?

| Approach    | Collision Risk | Length | Complexity |
|-------------|----------------|--------|------------|
| Random      | Yes (grows with scale) | 6-8 chars | Simple |
| MD5/SHA     | Low but present | Long (must truncate) | Medium |
| **Base62 from ID** | **Zero** | **6 chars** | **Simple** |

Base62 encodes the auto-incremented DB primary key. Same ID → same code. Mathematically collision-free.

### Why HTTP 302, not 301?
- **301 Permanent**: Browser caches redirect. Subsequent clicks never hit your server — analytics break.
- **302 Temporary**: Every click hits the server. Accurate click counting. ✅

### Why soft-delete?
Deleting a URL row would destroy all associated analytics in `url_access_logs`.
Setting `is_active = false` preserves the history while preventing future redirects.

### Fault tolerance: Redis failure
```java
try {
    return redisTemplate.opsForValue().get("url:" + shortCode);
} catch (Exception e) {
    log.warn("Redis unavailable — falling back to DB");
    return null;   // triggers DB lookup
}
```
Redis going down **never crashes the redirect**. The service degrades gracefully to DB-only mode.

---

## Scalability Strategy

- **Stateless backend**: No session data in the app. Any number of instances can run in parallel.
- **Horizontal scaling**: Add EC2 instances behind a load balancer. All share one RDS + one Redis.
- **Redis as shared cache**: Consistent cache state across all app instances.
- **DB indexing**: `shortCode` has a unique index — O(1) lookup regardless of table size.
- **Atomic click counter**: `UPDATE ... SET click_count = click_count + 1` is atomic in MySQL. No race conditions.

---

## Project Structure

```
url-shortener/
├── src/main/java/com/urlshortener/
│   ├── UrlShortenerApplication.java
│   ├── config/
│   │   └── RedisConfig.java
│   ├── controller/
│   │   └── UrlController.java
│   ├── dto/
│   │   ├── ErrorResponse.java
│   │   ├── UrlRequest.java
│   │   ├── UrlResponse.java
│   │   └── UrlStatsResponse.java
│   ├── exception/
│   │   ├── CustomAliasAlreadyExistsException.java
│   │   ├── GlobalExceptionHandler.java
│   │   ├── RateLimitExceededException.java
│   │   ├── UrlExpiredException.java
│   │   └── UrlNotFoundException.java
│   ├── model/
│   │   ├── Url.java
│   │   └── UrlAccessLog.java
│   ├── repository/
│   │   ├── UrlAccessLogRepository.java
│   │   └── UrlRepository.java
│   ├── service/
│   │   ├── RateLimitService.java
│   │   ├── UrlCleanupService.java
│   │   └── UrlService.java
│   └── util/
│       └── Base62Encoder.java
├── src/main/resources/
│   ├── application.yml
│   └── application-prod.yml
├── src/test/java/com/urlshortener/
│   └── Base62EncoderTest.java
├── docker-compose.yml
├── nginx.conf
├── deploy.sh
├── .env.example
└── pom.xml
```

---

## Documentation Index

| File | What's inside |
|---|---|
| `README.md` | Project overview, API reference, quick start |
| `DEPLOYMENT.md` | Step-by-step Railway (free) + AWS EC2 deployment |
| `TRADEOFFS.md` | Every design decision explained with alternatives |
| `RESUME_POINTS.md` | Copy-paste resume bullets + 10 interview Q&As |
| `docs/architecture.html` | Visual architecture diagram (open in browser) |
| `URL-Shortener.postman_collection.json` | Import into Postman — 11 ready-to-run requests |
| `docker-compose.yml` | Spin up MySQL + Redis locally in one command |
| `.env.example` | All environment variables documented |
