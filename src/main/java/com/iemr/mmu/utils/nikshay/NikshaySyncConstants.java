package com.iemr.mmu.utils.nikshay;

public final class NikshaySyncConstants {

    public static final String ST_PENDING = "PENDING";
    public static final String ST_SYNCED = "SYNCED";
    public static final String ST_LINKED_NO_ACTION = "LINKED_NO_ACTION";
    public static final String ST_HELD = "HELD";
    public static final String ST_FAILED = "FAILED";

    // nikshaySyncFailureReason
    public static final String RS_DEVICE_CONFIG_MISSING = "DEVICE_CONFIG_MISSING";
    public static final String RS_DEVICE_LOGIN_FAILED = "DEVICE_LOGIN_FAILED";
    public static final String RS_VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String RS_NIKSHAY_VALIDATION_ERROR = "NIKSHAY_VALIDATION_ERROR";
    public static final String RS_PORTAL_UNAVAILABLE = "PORTAL_UNAVAILABLE";
    public static final String RS_MAX_ATTEMPTS = "MAX_ATTEMPTS";
    public static final String RS_MULTIPLE_DUPLICATES = "MULTIPLE_DUPLICATE_RECORDS_FOUND";
    public static final String RS_CONTACT_TRACING_PENDING = "CONTACT_TRACING_PENDING";

    // link_status on t_nikshay_episode
    public static final String LINK_NEW = "NEW";
    public static final String LINK_LINKED = "LINKED";
    public static final String LINK_NEW_EPISODE = "NEW_EPISODE";
    public static final String LINK_NO_ACTION = "LINKED_NO_ACTION";

    public static final String DEVICE_TYPE_NAAT = "NAAT";
    public static final String DEVICE_TYPE_XRAY = "XRAY";
}
