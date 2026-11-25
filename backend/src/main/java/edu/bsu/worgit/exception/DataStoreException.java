package edu.bsu.worgit.exception;

import edu.bsu.worgit.domain.Messages;

/** Class diagram: DataStoreException (503 -> SYS-03, or an inline banner on forms). */
public class DataStoreException extends ApiException {
    public DataStoreException(Throwable cause) {
        super(Messages.DATA_STORE, cause);
    }

    @Override
    public int httpStatus() {
        return 503;
    }

    @Override
    public String code() {
        return "DATA_STORE";
    }
}
