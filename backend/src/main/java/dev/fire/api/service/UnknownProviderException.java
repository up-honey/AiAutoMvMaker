package dev.fire.api.service;

public class UnknownProviderException extends RuntimeException {

    public UnknownProviderException(String providerName) {
        super("Unknown video provider: " + providerName);
    }
}
