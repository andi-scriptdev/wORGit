package edu.bsu.worgit.exception;

import edu.bsu.worgit.domain.Messages;

/** 403 -> SYS-01 for wrong-role or out-of-scope access (ASSUMED in the spec). */
public class ForbiddenException extends ApiException {
    public ForbiddenException() {
        super(Messages.ACCESS_DENIED);
    }

    @Override
    public int httpStatus() {
        return 403;
    }

    @Override
    public String code() {
        return "ACCESS_DENIED";
    }
}
