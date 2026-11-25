package edu.bsu.worgit.exception;

/** Class diagram: InvalidInputException (400). Parent of the validation family below. */
public class InvalidInputException extends ApiException {
    private final String field;

    public InvalidInputException(String message) {
        this(message, null);
    }

    public InvalidInputException(String message, String field) {
        super(message);
        this.field = field;
    }

    @Override
    public int httpStatus() {
        return 400;
    }

    @Override
    public String code() {
        return "INVALID_INPUT";
    }

    @Override
    public String field() {
        return field;
    }
}
