package edu.bsu.worgit.exception;

public class InvalidMessageException extends InvalidInputException {
    public InvalidMessageException(String message) {
        super(message, "message");
    }

    @Override
    public String code() {
        return "INVALID_MESSAGE";
    }
}
