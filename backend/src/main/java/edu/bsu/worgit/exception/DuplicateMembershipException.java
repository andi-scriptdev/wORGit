package edu.bsu.worgit.exception;

import edu.bsu.worgit.domain.Messages;

public class DuplicateMembershipException extends InvalidInputException {
    public DuplicateMembershipException(String orgName) {
        super(Messages.alreadyMember(orgName));
    }

    @Override
    public int httpStatus() {
        return 409;
    }

    @Override
    public String code() {
        return "DUPLICATE_MEMBERSHIP";
    }
}
