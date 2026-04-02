package com.urlshortener.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Snowflake ID Generator — distributed, time-ordered unique ID generation.
 *
 * Why Snowflake instead of DB auto-increment?
 *  - Auto-increment requires a DB round-trip to get the ID before encoding
 *  - Snowflake generates IDs in-process — no DB needed, zero latency
 *  - Works correctly across multiple app instances simultaneously
 *  - IDs are time-ordered (monotonically increasing) — good for DB indexing
 *
 * Bit layout (64-bit long):
 *  ┌────────────────────────────────────────────────────────────┐
 *  │  1 bit  │   41 bits    │  10 bits   │     12 bits          │
 *  │  sign=0 │  timestamp   │ machine ID │  sequence number     │
 *  └────────────────────────────────────────────────────────────┘
 *
 *  - Timestamp:  milliseconds since custom epoch (Jan 1, 2024)
 *                → good for ~69 years from epoch
 *  - Machine ID: 0–1023, set per instance via config
 *                → supports up to 1024 app instances
 *  - Sequence:   0–4095 per millisecond per machine
 *                → 4096 IDs/ms per instance = 4 million/sec total
 *
 * Usage in UrlService:
 *  Replace: Url saved = repository.save(url); then encode(saved.getId())
 *  With:    String shortCode = base62Encoder.encode(snowflake.nextId())
 *           url.setShortCode(shortCode);
 *           repository.save(url);  // single save, no second round-trip
 *
 * Configuration (application.yml):
 *   app:
 *     snowflake:
 *       machine-id: 1   # unique per instance — set via env var in production
 */
@Component
@Slf4j
public class SnowflakeIdGenerator {

    // Custom epoch: 2024-01-01T00:00:00Z in milliseconds
    // Keeps timestamps small — extends the useful life of 41-bit field
    private static final long CUSTOM_EPOCH = 1704067200000L;

    private static final int  MACHINE_ID_BITS   = 10;
    private static final int  SEQUENCE_BITS     = 12;

    private static final long MAX_MACHINE_ID    = (1L << MACHINE_ID_BITS) - 1;  // 1023
    private static final long MAX_SEQUENCE      = (1L << SEQUENCE_BITS)  - 1;   // 4095

    private static final int  MACHINE_ID_SHIFT  = SEQUENCE_BITS;
    private static final int  TIMESTAMP_SHIFT   = SEQUENCE_BITS + MACHINE_ID_BITS;

    private final long machineId;

    private long lastTimestamp = -1L;
    private long sequence      =  0L;

    public SnowflakeIdGenerator(
            @Value("${app.snowflake.machine-id:1}") long machineId) {

        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new IllegalArgumentException(
                "machine-id must be between 0 and " + MAX_MACHINE_ID + ", got: " + machineId);
        }
        this.machineId = machineId;
        log.info("SnowflakeIdGenerator initialised with machineId={}", machineId);
    }

    /**
     * Generates the next unique Snowflake ID.
     * Thread-safe via synchronisation.
     *
     * @return a positive 64-bit long, globally unique across all instances
     */
    public synchronized long nextId() {
        long now = currentTimeMillis();

        if (now < lastTimestamp) {
            // Clock moved backwards — possible on some VMs or NTP adjustments.
            // Wait until clock catches up rather than generating duplicate IDs.
            long drift = lastTimestamp - now;
            log.warn("Clock moved backwards by {}ms — waiting to recover", drift);
            while (now < lastTimestamp) {
                now = currentTimeMillis();
            }
        }

        if (now == lastTimestamp) {
            // Same millisecond — increment sequence
            sequence = (sequence + 1) & MAX_SEQUENCE;

            if (sequence == 0) {
                // Sequence exhausted in this millisecond — busy-wait for next ms
                now = waitForNextMillis(lastTimestamp);
            }
        } else {
            // New millisecond — reset sequence
            sequence = 0L;
        }

        lastTimestamp = now;

        long id = ((now - CUSTOM_EPOCH) << TIMESTAMP_SHIFT)
                | (machineId             << MACHINE_ID_SHIFT)
                | sequence;

        log.debug("Generated Snowflake ID: {} (ts={}, machine={}, seq={})",
                  id, now - CUSTOM_EPOCH, machineId, sequence);

        return id;
    }

    private long waitForNextMillis(long lastTs) {
        long ts = currentTimeMillis();
        while (ts <= lastTs) {
            ts = currentTimeMillis();
        }
        return ts;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Decodes a Snowflake ID back into its components — useful for debugging.
     */
    public SnowflakeComponents decode(long id) {
        long timestamp  = (id >> TIMESTAMP_SHIFT) + CUSTOM_EPOCH;
        long machine    = (id >> MACHINE_ID_SHIFT) & MAX_MACHINE_ID;
        long seq        =  id & MAX_SEQUENCE;
        return new SnowflakeComponents(timestamp, machine, seq);
    }

    public record SnowflakeComponents(long timestamp, long machineId, long sequence) {}
}
