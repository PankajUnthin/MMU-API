package com.iemr.mmu.exception;

/**
 * Device row / village mapping / hierarchy id missing. Admin must fix configuration.
 */
public class NikshayConfigException extends NikshayException {
    public NikshayConfigException(String message) {
        super(message);
    }
}
