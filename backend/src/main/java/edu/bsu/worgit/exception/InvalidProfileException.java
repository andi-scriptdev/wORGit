package edu.bsu.worgit.exception;

public class InvalidProfileException extends InvalidInputException {
    public InvalidProfileException(String message, String field) {
        super(message, field);
    }

    @Override
    public String code() {
        return "INVALID_PROFILE";
    }
}
