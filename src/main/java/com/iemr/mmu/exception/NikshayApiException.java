package com.iemr.mmu.exception;

/**
 * Portal answered Success:false with a business/validation message. Not retried until data is fixed.
 */
public class NikshayApiException extends NikshayException {
    public NikshayApiException(String message, Integer httpStatus) {
        super(message, httpStatus, null);
    }
}
