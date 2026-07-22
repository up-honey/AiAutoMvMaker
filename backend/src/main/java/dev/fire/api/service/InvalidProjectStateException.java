package dev.fire.api.service;

public class InvalidProjectStateException extends RuntimeException {

    public InvalidProjectStateException() {
        super("Project cannot start from its current state");
    }
}
