# System Design — Trade-offs & Decisions

This document explains the key design choices in the URL shortener
and the trade-offs considered. Interviewers respect engineers who
can articulate *why* they made each decision.

---

## 1. Short Code Generation: Base62 vs Alternatives

| Approach | Pros | Cons | Decision |
|---|---|---|---|
| **Base62 from DB ID** ✅ | Zero collisions, deterministic, simple | Exposes sequential ID (predictable) | **Chosen** |
| Random string | Unpredictable codes | Collision risk, needs uniqueness check + retry | Rejected |
| MD5/SHA hash | Consistent output | Long output, must truncate, still has collision risk | Rejected |
| Snowflake ID | Distributed, no DB dependency | Overkill for this scale, complex setup | Phase 3 option |

**Trade-off accepted:** Sequential IDs mean someone could enumerate short codes (`aaaaaa`, `aaaaab`...). For a college project this is acceptable. In production, you'd XOR the ID with a secret to make it non-sequential while keeping it deterministic.

---

## 2. Caching: Cache-Aside vs Write-Through

| Pattern | How it works | Best for |
|---|---|---|
| **Cache-Aside** ✅ | App checks cache, miss → DB → populate cache | Read-heavy, occasional writes |
| Write-Through | Every DB write also updates cache | Write-heavy, cache always fresh |
| Write-Behind | Write to cache first, async flush to DB | Extremely write-heavy |

**Why Cache-Aside:** Redirects are overwhelmingly reads. A given short URL might be clicked 10,000 times but only created once. Cache-Aside gives us the read performance win where it matters most without complexity.

**Trade-off:** First request after a cache miss has full DB latency. Mitigated by priming the cache immediately on URL creation.

---

## 3. Click Tracking: Synchronous vs Asynchronous

**Current approach (synchronous):** Every redirect waits for the DB write to `url_access_logs` before returning.

| Approach | Latency | Data Loss Risk | Complexity |
|---|---|---|---|
| **Synchronous** ✅ | Higher (~5ms extra) | None | Low |
| Async (thread pool) | Lower | Some (if app crashes) | Medium |
| Message queue (Kafka) | Lowest | Near-zero | High |

**Trade-off accepted:** For a college project, synchronous is correct. The latency overhead is acceptable and the code is simple and debuggable. At 10x scale the correct solution is a write-behind queue — mentioned in `RESUME_POINTS.md` as the scale-up answer.

---

## 4. HTTP 301 vs 302 for Redirects

| Code | Browser caches? | Analytics work? | SEO benefit |
|---|---|---|---|
| 301 Permanent | Yes — forever | ❌ Breaks after first click | Good (if you want it) |
| **302 Temporary** ✅ | No | ✅ Every click tracked | None |

**Decision:** 302 always. We are a tracking service — the entire analytics feature breaks with 301. Any SEO consideration is irrelevant for a URL shortener where the destination URL owns its own SEO.

---

## 5. Soft Delete vs Hard Delete

| Approach | Analytics preserved? | Storage cost | Complexity |
|---|---|---|---|
| **Soft Delete** ✅ (`is_active = false`) | ✅ Yes | Slightly higher | Low |
| Hard Delete | ❌ Loses all logs | Lower | Low |
| Archive to S3 | ✅ Yes | Very low | High |

**Decision:** Soft delete. Deleting a URL should not erase the history of who clicked it. The `url_access_logs` rows would be orphaned or cascade-deleted with a hard delete. Disk space is cheap; analytics data is not recoverable once lost.

---

## 6. Rate Limiting: Nginx vs Redis vs Spring Interceptor

| Layer | Scope | Works distributed? | Setup complexity |
|---|---|---|---|
| Nginx (network) | Per-server | ❌ Each server independent | Low |
| **Redis counter** ✅ | Per-IP globally | ✅ Shared across all instances | Medium |
| Spring Interceptor (in-memory) | Per-instance | ❌ Each instance independent | Low |

**Decision:** Redis-based rate limiting. A single-instance app could use in-memory, but since the architecture supports horizontal scaling, rate limits must be shared. Redis INCR + EXPIRE is O(1) and atomic — no race conditions.

**Trade-off:** If Redis is down, rate limiting fails open (requests are allowed through). This is intentional — service availability is more important than perfect rate limiting. A malicious burst during a Redis outage is an acceptable trade-off over refusing all legitimate traffic.

---

## 7. Relational DB vs NoSQL for URL Storage

| Database | Strong at | Weak at |
|---|---|---|
| **MySQL** ✅ | ACID transactions, indexed lookups, analytics queries | Horizontal sharding at extreme scale |
| DynamoDB / MongoDB | Horizontal scale, flexible schema | Complex queries, joins |
| Cassandra | Write-heavy, massive scale | Read patterns, consistency |

**Decision:** MySQL. The access pattern is simple (lookup by short_code), the data is highly relational (url → logs), and ACID compliance ensures click_count increments are correct. At billion-URL scale you'd shard MySQL or move to DynamoDB — but that's not this project.

---

## 8. Expiry: Application-Level vs DB-Level

| Approach | Latency on expired access | Automatic cleanup | Complexity |
|---|---|---|---|
| **Application check** ✅ | Immediate (in-request) | Via scheduled job | Low |
| DB TTL / Event Scheduler | Delayed (depends on job interval) | Automatic | Medium |
| Redis TTL only | Immediate for cached URLs | Automatic in Redis | High (cache as truth) |

**Decision:** Application-level check on every request (immediate feedback) + scheduled cleanup job (keeps DB and cache clean). Two-layer defence: user gets an instant 410 Gone, and the background job ensures expired URLs don't accumulate indefinitely.

---

## Known Limitations & How to Fix Them

| Limitation | Fix at Scale |
|---|---|
| Sequential short codes are guessable | XOR ID with a secret salt before Base62 encoding |
| Synchronous access log writes add latency | Publish to Kafka/SQS queue; consumer writes to DB asynchronously |
| Single MySQL instance is a write bottleneck | Read replica for analytics queries; primary for writes only |
| click_count can race under extreme concurrency | Already mitigated with atomic SQL UPDATE; at higher scale use Redis counter + periodic DB sync |
| No user authentication | Add Spring Security + JWT for user-owned link management |
| No URL malware scanning | Integrate Google Safe Browsing API on URL creation |
