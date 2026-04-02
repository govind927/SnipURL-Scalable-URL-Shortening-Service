package com.urlshortener.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String ip) {
        super("Rate limit exceeded for IP: " + ip + ". Please try again later.");
    }
}
