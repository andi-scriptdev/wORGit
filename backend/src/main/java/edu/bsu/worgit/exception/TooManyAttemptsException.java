package edu.bsu.worgit.exception;

import edu.bsu.worgit.domain.Messages;

/** Login rate limiting. Status, code and wording are ASSUMED: the spec only says "login rate-limiting". */
public class TooManyAttemptsException extends ApiException {
    public TooManyAttemptsException() {
        super(Messages.TOO_MANY_ATTEMPTS);
    }

    @Override
    public int httpStatus() {
        return 429;
    }

    @Override
    public String code() {
        return "TOO_MANY_ATTEMPTS";
    }
}
