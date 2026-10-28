package com.iemr.mmu.exception;

/**
 * Credentials rejected (wrong/expired password). Never auto-retried – risk of lockout.
 */
public class NikshayLoginRejectedException extends NikshayException {
    public NikshayLoginRejectedException(String message) {
        super(message);
    }

    public NikshayLoginRejectedException(String message, Integer httpStatus) {
        super(message, httpStatus, null);
    }
}
