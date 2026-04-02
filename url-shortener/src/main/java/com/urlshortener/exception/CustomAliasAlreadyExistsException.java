package com.urlshortener.exception;

public class CustomAliasAlreadyExistsException extends RuntimeException {
    public CustomAliasAlreadyExistsException(String alias) {
        super("Custom alias is already taken: " + alias);
    }
}
