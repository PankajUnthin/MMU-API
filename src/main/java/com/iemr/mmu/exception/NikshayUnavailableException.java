package com.iemr.mmu.exception;

/**
 * Portal down, timeout, I/O error, 5xx – transient, retried with back-off.
 */
public class NikshayUnavailableException extends NikshayException {

    public NikshayUnavailableException(String message, Integer httpStatus) {
        super(message, httpStatus, null);
    }

    public NikshayUnavailableException(String ssoReturnedNoSessionKey, Throwable o) {
        super(o.getMessage());
    }
}
