package dev.fire.api.service;

public class PaidGenerationConfirmationRequiredException extends RuntimeException {

    public PaidGenerationConfirmationRequiredException(String providerName) {
        super("Paid generation must be explicitly confirmed for provider " + providerName);
    }
}
