package com.iemr.mmu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * nikshay.* properties. Two environments: uat (beta.nikshay.in) and production (www.nikshay.in).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "nikshay")
public class NikshayProperties {

    /**
     * uat | production
     */
    private String env = "uat";

    private Env uat = new Env(
            "https://sso.beta.nikshay.in",
            "https://beta.nikshay.in",
            "29",
            "SSOAuthorizationCookieBeta");
    private Env production = new Env(
            "https://sso.nikshay.in",
            "https://www.nikshay.in",
            "29",
            "SSOAuthorizationCookie");

    private Http http = new Http();
    private Sso sso = new Sso();
    private Job job = new Job();
    private Credential credential = new Credential();

    public Env active() {
        return "production".equalsIgnoreCase(env) ? production : uat;
    }

    @Data
    public static class Env {
        private String ssoBase;
        private String origin;
        private String clientId;
        private String jwtCookieName;

        public Env() {
        }

        public Env(String ssoBase, String origin, String clientId, String jwtCookieName) {
            this.ssoBase = ssoBase;
            this.origin = origin;
            this.clientId = clientId;
            this.jwtCookieName = jwtCookieName;
        }

        public String loginUrl() {
            return ssoBase + "/v1/sso/login?returnUrl=" + origin + "/Home&clientId=" + clientId;
        }

        public String homeUrl() {
            return origin + "/Home";
        }
    }

    @Data
    public static class Http {
        private int connectTimeoutMs = 15_000;
        private int readTimeoutMs = 60_000;
        private String userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
        /**
         * max chars of a response body kept in the sync-history JSON columns
         */
        private int logBodyMaxChars = 4000;
    }

    /**
     * Form field names on the SSO login page.  // VERIFY against a UAT network capture
     * The Playwright tool located them by label ("User Name", "Password", "captcha"); the
     * underlying name attributes are assumed here and must be confirmed once.
     */
    @Data
    public static class Sso {
        private String usernameField = "username";
        private String passwordField = "password";
        private String captchaField = "captcha";
        private String clientIdField = "clientId";
        private String encryptedFlagField = "isEncryptedData";
        private String externalLoginField = "isExternalLogin";
        private String returnUrlField = "returnUrl";
        /**
         * POST target for the login form; empty = same URL as the login page
         */
        private String formAction = "";
        private int loginRetryCount = 2;
    }

    @Data
    public static class Job {
        private boolean enabled = true;
        /**
         * Quartz cron for the ID-generation job
         */
        private String enrollCron = "0 0 */2 * * ?";
        private int batchSize = 200;
        private int maxAttempts = 3;
        private int retryBackoffMinutes = 30;
        private int callDelayMs = 500;
        /**
         * consecutive portal failures on one device after which the rest of that bucket is skipped
         */
        private int consecutiveFailThreshold = 5;
        /**
         * true = send IsPossibleDuplicate=true & create when portal hits exist but none is a firm match (JS behaviour)
         */
        private boolean createOnLooseMatch = true;
    }

    @Data
    public static class Credential {
        private String keyBase64;
    }
}
