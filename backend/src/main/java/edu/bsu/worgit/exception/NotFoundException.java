package edu.bsu.worgit.exception;

import edu.bsu.worgit.domain.Messages;

/** 404 -> SYS-04. Not in the class diagram; required by the spec's HTTP mapping. */
public class NotFoundException extends ApiException {
    public NotFoundException() {
        super(Messages.NOT_FOUND);
    }

    @Override
    public int httpStatus() {
        return 404;
    }

    @Override
    public String code() {
        return "NOT_FOUND";
    }
}
