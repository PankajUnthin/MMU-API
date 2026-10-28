package com.iemr.mmu.exception;

/**
 * Base type for every Ni-kshay integration failure.
 */
public class NikshayException extends RuntimeException {
    private final Integer httpStatus;

    public NikshayException(String message) {
        this(message, null, null);
    }

    public NikshayException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public NikshayException(String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
