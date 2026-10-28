package com.iemr.mmu.data.nikshay.dtos;

public enum NikshaySyncFailureReason {
    DEVICE_CONFIG_MISSING,
    DEVICE_LOGIN_FAILED,
    VALIDATION_FAILED,
    NIKSHAY_VALIDATION_ERROR,
    PORTAL_UNAVAILABLE,
    MAX_ATTEMPTS,
    MULTIPLE_DUPLICATE_RECORDS_FOUND,
    CONTACT_TRACING_PENDING;

    /** Reasons the job retries on its own; the rest wait for a human. */
    public boolean isRetryable() {
        return this == PORTAL_UNAVAILABLE || this == CONTACT_TRACING_PENDING;
    }
}
