package com.agentic.sdlc.shortener.service;

public class AliasAlreadyTakenException extends RuntimeException {
    public AliasAlreadyTakenException(String alias) {
        super("Alias '" + alias + "' is already taken");
    }
}
