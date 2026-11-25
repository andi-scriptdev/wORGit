package edu.bsu.worgit.exception;

import edu.bsu.worgit.domain.Messages;

public class CertificateNotEligibleException extends InvalidInputException {
    public CertificateNotEligibleException(String studentName) {
        super(Messages.notEligible(studentName));
    }

    @Override
    public int httpStatus() {
        return 422;
    }

    @Override
    public String code() {
        return "CERTIFICATE_NOT_ELIGIBLE";
    }
}
