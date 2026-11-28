package edu.bsu.worgit.exception;

import edu.bsu.worgit.domain.Messages;

public class InvalidPasswordException extends InvalidInputException {
    public InvalidPasswordException() {
        super(Messages.PASSWORD_TOO_SHORT, "newPassword");
    }

    /** e.g. "Current password is incorrect." under the currentPassword field. */
    public InvalidPasswordException(String message, String field) {
        super(message, field);
    }

    @Override
    public String code() {
        return "INVALID_PASSWORD";
    }
}
