package com.iemr.mmu.exception;

/**
 * Session cookie no longer accepted (401/403 or redirect to SSO). Caller re-logs in once.
 */
public class NikshaySessionExpiredException extends NikshayException {
    public NikshaySessionExpiredException(String message, Integer httpStatus) {
        super(message, httpStatus, null);
    }
}
