package edu.bsu.worgit.exception;

import edu.bsu.worgit.domain.Messages;

public class InvalidPasswordException extends InvalidInputException {
    public InvalidPasswordException() {
        super(Messages.PASSWORD_TOO_SHORT, "newPassword");
    }

    @Override
    public String code() {
        return "INVALID_PASSWORD";
    }
}
