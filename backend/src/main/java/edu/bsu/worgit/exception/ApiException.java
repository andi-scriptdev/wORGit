package edu.bsu.worgit.exception;

/**
 * Common parent of every error the API reports. It carries the HTTP status and a stable code that the
 * Expo app maps to a screen or an inline message (see README). Unchecked, unlike the class diagram.
 */
public abstract class ApiException extends RuntimeException {

    protected ApiException(String message) {
        super(message);
    }

    protected ApiException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract int httpStatus();

    public abstract String code();

    /** The form field the message belongs to, or null. */
    public String field() {
        return null;
    }
}
