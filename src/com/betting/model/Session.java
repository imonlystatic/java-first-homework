package com.betting.model;

/**
 * Session model
 */
public class Session {

    // session last time: 10 minutes
    private static final long SESSION_TTL_MS = 10 * 60 * 1000L;

    // session key
    private final String key;
    // customerId
    private final int customerId;
    // expire time
    private volatile long expiresAt;

    public Session(String key, int customerId) {
        this.key = key;
        this.customerId = customerId;
        this.expiresAt = System.currentTimeMillis() + SESSION_TTL_MS;
    }

    public String getKey() {
        return key;
    }

    public int getCustomerId() {
        return customerId;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    public void renew() {
        expiresAt = System.currentTimeMillis() + SESSION_TTL_MS;
    }
}
