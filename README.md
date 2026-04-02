# SnipURL — Scalable URL Shortening Service

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen?style=flat-square)
![Redis](https://img.shields.io/badge/Redis-7-red?style=flat-square)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?style=flat-square)
![React](https://img.shields.io/badge/React-18-61dafb?style=flat-square)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ed?style=flat-square)
![JWT](https://img.shields.io/badge/Auth-JWT-purple?style=flat-square)

A high-performance, production-grade URL shortening service built with Java and Spring Boot. Converts long URLs into compact, shareable links with sub-millisecond redirect latency via Redis caching, full click analytics, JWT authentication, QR code generation, and link preview pages.

---

## Features

**Core**
- URL shortening using Base62 encoding from auto-incremented DB IDs — mathematically collision-free
- Snowflake ID generator for distributed, time-ordered unique ID generation
- HTTP 302 redirects — intentional, ensures every click is tracked server-side
- Link expiry with request-time validation and hourly scheduled cleanup
- Soft delete — preserves analytics history when links are deactivated
- Custom aliases — users can choose their own short code

**Performance**
- Redis Cache-Aside pattern — hot URLs served in under 1ms without hitting the database
- Graceful Redis fault tolerance — falls back to MySQL silently if cache is unavailable
- Atomic SQL click counter — no race conditions under concurrent load
- Optimized database indexes on `short_code` and `accessed_at`

**Analytics**
- Event-level click tracking — every redirect stored in `url_access_logs`
- Geo tracking via ip-api.com — country and city per click
- 30-day click trend chart, country breakdown, recent clicks table
- Separate `click_count` summary counter for fast dashboard display

**Security and Auth**
- JWT authentication — register, login, 24-hour token expiry
- BCrypt password hashing — plain text never stored
- Redis-based rate limiting per IP — works across horizontal instances
- URL validation — must start with http:// or https://
- IP masking in analytics responses — last octet hidden for privacy

**Advanced**
- QR code generation — ZXing library, 300x300 PNG, downloadable
- Link preview page — scrapes og:title, og:image, og:description via Jsoup, 5-second countdown before redirect
- Spring Actuator — health checks and metrics endpoints
- Scheduled cleanup — hourly job deactivates expired URLs and evicts Redis keys

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21 + Spring Boot 3.2 |
| Database | MySQL 8.0 |
| Cache | Redis 7 |
| Security | Spring Security + JWT (jjwt) |
| QR Code | Google ZXing 3.5 |
| HTML Parsing | Jsoup 1.17 |
| Frontend | React 18 + Recharts |
| Local Infra | Docker Compose |
| Build Tool | Maven 3.8 |

---

## System Architecture

```
Browser (React :3000)
        │
        ▼
Spring Boot API (:8080)
        │
   ┌────┴──────────┐
   ▼               ▼
Redis :6379     MySQL :3307
(Cache-Aside)   (Source of Truth)
        │
        ▼
  url_access_logs
  (Event Analytics)
```

**Cache-Aside Flow (every redirect):**
```
GET /{shortCode}
    │
    ├──► Redis.get("url:{code}")
    │         │
    │      HIT ──► return URL instantly (< 1ms)
    │         │
    │      MISS──► MySQL.findByShortCode()
    │                │
    │                ├──► validate (active? expired?)
    │                ├──► Redis.set(key, url, TTL=24h)
    │                └──► return URL + log access event
    │
    └──► Redis down? → silently fallback to DB
```

---

## Project Structure

```
url-shortener/
├── src/main/java/com/urlshortener/
│   ├── config/
│   │   ├── RedisConfig.java
│   │   └── SecurityConfig.java
│   ├── controller/
│   │   ├── UrlController.java
│   │   ├── AuthController.java
│   │   ├── DashboardController.java
│   │   └── PreviewController.java
│   ├── dto/
│   │   ├── UrlRequest.java
│   │   ├── UrlResponse.java
│   │   ├── UrlStatsResponse.java
│   │   ├── AuthDto.java
│   │   └── ErrorResponse.java
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java
│   │   ├── UrlNotFoundException.java
│   │   ├── UrlExpiredException.java
│   │   ├── CustomAliasAlreadyExistsException.java
│   │   └── RateLimitExceededException.java
│   ├── model/
│   │   ├── Url.java
│   │   ├── UrlAccessLog.java
│   │   └── User.java
│   ├── repository/
│   │   ├── UrlRepository.java
│   │   ├── UrlAccessLogRepository.java
│   │   └── UserRepository.java
│   ├── security/
│   │   ├── JwtService.java
│   │   ├── JwtAuthFilter.java
│   │   └── UserDetailsServiceImpl.java
│   ├── service/
│   │   ├── UrlService.java
│   │   ├── AuthService.java
│   │   ├── DashboardService.java
│   │   ├── RateLimitService.java
│   │   ├── GeoLocationService.java
│   │   ├── QrCodeService.java
│   │   ├── LinkPreviewService.java
│   │   └── UrlCleanupService.java
│   ├── util/
│   │   ├── Base62Encoder.java
│   │   └── SnowflakeIdGenerator.java
│   └── UrlShortenerApplication.java
│
├── src/main/resources/
│   ├── application.yml
│   └── application-prod.yml
│
├── src/test/java/com/urlshortener/
│   ├── Base62EncoderTest.java
│   ├── SnowflakeIdGeneratorTest.java
│   ├── service/UrlServiceTest.java
│   └── controller/UrlControllerTest.java
│
├── frontend/
│   └── src/
│       ├── App.jsx
│       ├── components/
│       │   ├── ShortenForm.jsx
│       │   ├── AnalyticsDashboard.jsx
│       │   ├── Dashboard.jsx
│       │   ├── AuthPage.jsx
│       │   └── PreviewPage.jsx
│       └── services/api.js
│
├── docs/
│   ├── architecture.html
│   └── plantuml/
│       ├── 01_system_architecture.puml
│       ├── 02_class_diagram.puml
│       ├── 03_database_er.puml
│       ├── 04_sequence_shorten.puml
│       ├── 05_sequence_redirect.puml
│       ├── 06_sequence_auth.puml
│       ├── 07_component_diagram.puml
│       ├── 08_state_url_lifecycle.puml
│       └── 09_deployment_diagram.puml
│
├── docker-compose.yml
├── Dockerfile
├── nginx.conf
├── pom.xml
├── RESUME_POINTS.md
├── TRADEOFFS.md
└── .github/workflows/ci-cd.yml
```

---

## Database Schema

**`urls` table**

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | Auto-increment, used for Base62 encoding |
| long_url | TEXT | Original URL, max 2048 chars |
| short_code | VARCHAR(50) | UNIQUE INDEX — O(1) lookup |
| created_at | DATETIME | Auto-set on insert |
| expiry_time | DATETIME | Nullable — null means never expires |
| click_count | BIGINT | Fast summary counter |
| is_active | BOOLEAN | Soft delete flag |
| custom_alias | VARCHAR(50) | User-defined code |
| creator_ip | VARCHAR(45) | For rate limiting |
| user_id | BIGINT FK | Nullable — null for anonymous users |

**`url_access_logs` table**

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| short_code | VARCHAR(50) | Indexed |
| accessed_at | DATETIME | Indexed |
| ip_address | VARCHAR(45) | |
| user_agent | VARCHAR(512) | |
| country | VARCHAR(100) | From ip-api.com |
| city | VARCHAR(100) | From ip-api.com |

**`users` table**

| Column | Type | Notes |
|---|---|---|
| id | BIGINT PK | |
| email | VARCHAR(100) | UNIQUE INDEX |
| password | VARCHAR(255) | BCrypt hashed |
| name | VARCHAR(50) | |
| created_at | DATETIME | |
| is_active | BOOLEAN | |

---

## API Reference

### Create Short URL
```
POST /api/shorten
Content-Type: application/json

{
  "longUrl":     "https://example.com/very/long/url",
  "customAlias": "my-link",
  "expiryTime":  "2025-12-31T23:59"
}
```
Response `201 Created`

### Redirect
```
GET /{shortCode}
→ 302 Found
→ 404 Not Found
→ 410 Gone (expired)
```

### Analytics
```
GET /api/stats/{shortCode}
→ 200 OK
```

### Link Preview
```
GET /api/preview/{shortCode}
→ 200 OK with og: metadata
```

### QR Code
```
GET /api/qr/{shortCode}?size=300
→ 200 OK image/png
```

### Auth
```
POST /api/auth/register   { name, email, password }
POST /api/auth/login      { email, password }
→ Both return JWT token
```

### Dashboard (JWT required)
```
GET    /api/my-urls
DELETE /api/my-urls/{shortCode}
```

### Health
```
GET /api/health
GET /actuator/health
```

---

## Running Locally

### Prerequisites
- Java 21
- Maven 3.8+
- Docker Desktop
- Node.js 22+

### Start (3 terminals)

```bash
# Terminal 1 — databases
docker-compose up -d

# Terminal 2 — backend
mvn spring-boot:run

# Terminal 3 — frontend
cd frontend
npm install   # first time only
npm start
```

Open **http://localhost:3000**

### Stop
```bash
docker-compose down
# Ctrl+C in other terminals
```

---

## Running Tests

```bash
mvn test
```

Covers:
- `Base62EncoderTest` — 6 tests
- `SnowflakeIdGeneratorTest` — 7 tests including concurrency test
- `UrlServiceTest` — 11 unit tests with Mockito
- `UrlControllerTest` — 10 MVC slice tests

---

## Key Design Decisions

**Why Base62 over random or hash?**
Auto-increment ID → Base62 encoded = guaranteed collision-free. 6 characters = 56 billion unique codes. Random strings risk collisions at scale. MD5 truncation reintroduces collision risk.

**Why HTTP 302 not 301?**
301 is permanent — browsers cache it forever. After the first visit, subsequent clicks never reach the server, completely breaking click analytics. 302 ensures every click is tracked.

**Why soft delete?**
Hard deleting a URL destroys all associated `url_access_logs`. Soft delete (`is_active = false`) stops future redirects while preserving full analytics history.

**Why a separate `url_access_logs` table?**
`click_count` is a fast integer for displaying totals. `url_access_logs` stores full event data — needed to answer questions like "how many clicks from India last week?" A single counter cannot do this.

**Why Redis-based rate limiting?**
In-memory rate limiting breaks with multiple app instances — each has its own counter. Redis is shared, so limits apply globally regardless of how many instances are running.

**Redis fault tolerance:**
```java
try {
    return redisTemplate.opsForValue().get("url:" + shortCode);
} catch (Exception e) {
    return null; // silently fall through to DB
}
```
Redis failure never crashes redirects. Service degrades gracefully.

---

## UML Diagrams

All diagrams in `docs/plantuml/`. Paste any `.puml` file contents at **https://www.plantuml.com/plantuml/uml**

| Diagram | File |
|---|---|
| System Architecture | `01_system_architecture.puml` |
| Class Diagram | `02_class_diagram.puml` |
| Database ER | `03_database_er.puml` |
| Shorten URL Sequence | `04_sequence_shorten.puml` |
| Redirect + Cache-Aside Sequence | `05_sequence_redirect.puml` |
| JWT Auth Sequence | `06_sequence_auth.puml` |
| Component Diagram | `07_component_diagram.puml` |
| URL Lifecycle State | `08_state_url_lifecycle.puml` |
| Deployment Diagram | `09_deployment_diagram.puml` |

---

## Documentation

| File | Contents |
|---|---|
| `RESUME_POINTS.md` | 20 resume bullet points + 10 interview Q&As |
| `TRADEOFFS.md` | Every design decision with alternatives considered |
| `docs/architecture.html` | Visual architecture diagram — open in browser |