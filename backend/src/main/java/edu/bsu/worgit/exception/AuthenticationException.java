package edu.bsu.worgit.exception;

import edu.bsu.worgit.domain.Messages;

/** Wrong ID or password (401). Same message for an unknown ID and a wrong password. */
public class AuthenticationException extends InvalidInputException {
    public AuthenticationException() {
        super(Messages.AUTH_FAILED);
    }

    @Override
    public int httpStatus() {
        return 401;
    }

    @Override
    public String code() {
        return "AUTHENTICATION_FAILED";
    }
}
