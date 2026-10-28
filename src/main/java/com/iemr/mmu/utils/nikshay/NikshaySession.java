package com.iemr.mmu.utils.nikshay;

import lombok.Builder;
import lombok.Value;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * One logged-in Ni-kshay session for one device login.
 * Holds the cookie jar; nothing else in the app touches cookies.
 * Not thread-safe — one session is used by one device loop at a time.
 */

@Value
@Builder
public class NikshaySession {

    private final Integer deviceConfigId;
    private final String phiUserId;
    private String deviceId;
    private String origin;
    private final String env;
    private String cookieHeader;
    private final Instant loggedInAt = Instant.now();

    /**
     * domain (lower-case, no leading dot) -> cookie name -> value
     */
    private final Map<String, Map<String, String>> jar = new LinkedHashMap<>();

    public NikshaySession(Integer deviceConfigId, String phiUserId, String deviceId, String origin, String env, String cookieHeader) {
        this.deviceConfigId = deviceConfigId;
        this.phiUserId = phiUserId;
        this.deviceId = deviceId;
        this.origin = origin;
        this.env = env;
        this.cookieHeader = cookieHeader;
    }

    /**
     * Store a cookie. Domain is the Set-Cookie Domain attribute if present, else the request host.
     */
    public void put(String domain, String name, String value) {
        String d = normalize(domain);
        jar.computeIfAbsent(d, k -> new LinkedHashMap<>()).put(name, value);
    }

    /**
     * Cookie header for a request to the given URI (host match or parent-domain match).
     */
    public String cookieHeaderFor(URI uri) {
        String host = uri.getHost().toLowerCase();
        StringJoiner sj = new StringJoiner("; ");
        for (Map.Entry<String, Map<String, String>> e : jar.entrySet()) {
            String d = e.getKey();
            if (host.equals(d) || host.endsWith("." + d)) {
                e.getValue().forEach((n, v) -> sj.add(n + "=" + v));
            }
        }
        return sj.length() == 0 ? null : sj.toString();
    }

    public boolean hasCookie(String name) {
        return jar.values().stream().anyMatch(m -> m.containsKey(name));
    }

    public void clear() {
        jar.clear();
    }

    private static String normalize(String domain) {
        String d = domain.toLowerCase().trim();
        return d.startsWith(".") ? d.substring(1) : d;
    }

    public Integer getDeviceConfigId() {
        return deviceConfigId;
    }

    public String getPhiUserId() {
        return phiUserId;
    }

    public String getEnv() {
        return env;
    }

    public Instant getLoggedInAt() {
        return loggedInAt;
    }

    @Override
    public String toString() {
        return "NikshaySession{device=" + deviceConfigId + ", user=" + phiUserId + ", domains=" + jar.keySet() + "}";
    }
}
