package com.iemr.mmu.utils.nikshay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.iemr.mmu.config.NikshayProperties;
import com.iemr.mmu.data.nikshay.dtos.AddIndiaTbRequest;
import com.iemr.mmu.data.nikshay.dtos.AddIndiaTbResponse;
import com.iemr.mmu.data.nikshay.dtos.DuplicateCandidateDto;
import com.iemr.mmu.data.nikshay.dtos.DuplicateCheckRequest;
import com.iemr.mmu.data.nikshay.dtos.DuplicateCheckResult;
import com.iemr.mmu.data.nikshay.dtos.NikshayApiResponse;
import com.iemr.mmu.data.nikshay.entity.NikshayDeviceConfiguration;
import com.iemr.mmu.exception.NikshayApiException;
import com.iemr.mmu.exception.NikshayConfigException;
import com.iemr.mmu.exception.NikshayLoginRejectedException;
import com.iemr.mmu.exception.NikshaySessionExpiredException;
import com.iemr.mmu.exception.NikshayUnavailableException;
import com.iemr.mmu.service.nikshay.NikshayCredentialCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The ONLY class that talks HTTP to Ni-kshay.
 *
 *  • login()/logout()   – SSO login ported from nikshay-enroll/lib/auth.js (submitWithOwnHashing path):
 *                         clientId → authToken → salt → session-key → sha256(sessionKey + sha256(salt+pwd))
 *                         → POST form → follow redirects → cookies for the portal origin → verify.
 *                         Reused by every scheduler (NAAT enroll/test, X-ray test): pass whichever
 *                         device row you have.
 *  • getPossibleDuplicates / addIndiaTb – the two portal calls this release needs, cookie from
 *                         NikshaySession, response parsed into DTOs, typed exceptions on failure.
 *
 * Nothing here touches the database.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NikshayClient {

    private final NikshayProperties props;
    private final NikshayCredentialCipher cipher;
    private final ObjectMapper om;

    // ======================================================================
    //  LOGIN / LOGOUT
    // ======================================================================

    /**
     * Log in with the device's wrapper PHI credentials and return a session.
     * Retries network/5xx errors up to sso.loginRetryCount times; a rejected credential is thrown
     * immediately as NikshayLoginRejectedException and is NEVER retried.
     */
    public NikshaySession login(NikshayDeviceConfiguration device) {
        if (device.getPhiUserId() == null || device.getPassword() == null)
            throw new NikshayConfigException("Device " + device.getDeviceId() + " has no wrapper login configured");

        String password = cipher.decrypt(device.getPassword());
        int attempts = Math.max(1, props.getSso().getLoginRetryCount() + 1);
        NikshayUnavailableException last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                return doLogin(device, password);
            } catch (NikshayUnavailableException e) {
                last = e;
                log.warn("Ni-kshay login attempt {}/{} for {} failed: {}", i, attempts, device.getPhiUserId(), e.getMessage());
                sleep(1500L * i);
            }
        }
        throw last;
    }

    private NikshaySession doLogin(NikshayDeviceConfiguration device, String password) {
        NikshayProperties.Env env = props.active();
        NikshayProperties.Sso sso = props.getSso();

        // fresh cookie jar per login – never share between devices
        CookieManager jar = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient http = HttpClient.newBuilder()
                .cookieHandler(jar)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(props.getHttp().getConnectTimeoutMs()))
                .build();

        // 1. login page → clientId (+ captcha text if rendered server side) + cookies
        String loginUrl = env.loginUrl();
        HttpResponse<String> page = send(http, HttpRequest.newBuilder(URI.create(loginUrl))
                .header("User-Agent", props.getHttp().getUserAgent()).GET());
        String clientId = firstGroup(page.body(), "name=\"" + sso.getClientIdField() + "\"[^>]*value=\"([^\"]+)\"");
        if (clientId == null) clientId = env.getClientId();
        String captcha = firstGroup(page.body(), "id=\"capText\"[^>]*>\\s*([A-Za-z0-9]{4,})\\s*<");
        if (captcha == null) captcha = randomCaptcha();

        // 2. auth token
        String authToken = jsonData(getJson(http, env.getSsoBase() + "/v1/ssoserver/client",
                Map.of("X-SSO-Client-Id", clientId)), "authToken");
        if (authToken == null) throw new NikshayUnavailableException("SSO did not issue an auth token", (Throwable) null);

        // 3. salt for this user
        JsonNode saltRes = getJson(http,
                env.getSsoBase() + "/v1/user/saltKey?username=" + URLEncoder.encode(device.getPhiUserId(), StandardCharsets.UTF_8),
                Map.of("X-SSO-Client-Id", clientId, "X-SSO-Auth-Token", authToken));
        if (!saltRes.path("success").asBoolean(false) || saltRes.path("data").isMissingNode())
            throw new NikshayLoginRejectedException("SSO returned no salt for user " + device.getPhiUserId() + " (unknown username?)");
        String salt = saltRes.path("data").asText();

        // 4. session key (fresh token first, as the page does)
        String authToken2 = jsonData(getJson(http, env.getSsoBase() + "/v1/ssoserver/client",
                Map.of("X-SSO-Client-Id", clientId)), "authToken");
        JsonNode skRes = getJson(http, env.getSsoBase() + "/v1/ssoserver/session-key",
                Map.of("X-SSO-Client-Id", clientId, "X-SSO-Auth-Token", authToken2 == null ? "" : authToken2));
        if (!skRes.path("success").asBoolean(false)) throw new NikshayUnavailableException("SSO returned no session key", 500);
        String sessionKey = skRes.path("data").asText();

        // 5. two-round SHA-256 exactly as the page's JS does
        String digest = sha256Hex(salt + password);
        digest = sha256Hex(sessionKey + digest);

        // 6. POST the login form                                   // VERIFY field names / action on UAT capture
        String formAction = sso.getFormAction() == null || sso.getFormAction().isBlank() ? loginUrl : sso.getFormAction();
        Map<String, String> form = new LinkedHashMap<>();
        form.put(sso.getUsernameField(), device.getPhiUserId());
        form.put(sso.getPasswordField(), digest);
        form.put(sso.getCaptchaField(), captcha);
        form.put(sso.getClientIdField(), clientId);
        form.put(sso.getEncryptedFlagField(), "true");
        form.put(sso.getExternalLoginField(), "false");
        form.put(sso.getReturnUrlField(), env.homeUrl());
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpResponse<String> post = send(http, HttpRequest.newBuilder(URI.create(formAction))
                .header("User-Agent", props.getHttp().getUserAgent())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", env.getSsoBase())
                .header("Referer", loginUrl)
                .POST(HttpRequest.BodyPublishers.ofString(body)));

        // 7. did we land on the portal?  (redirects were followed)
        String finalUrl = post.uri().toString();
        boolean onPortal = finalUrl.startsWith(env.getOrigin());
        if (!onPortal) {
            String err = firstGroup(post.body(), "id=\"setError\"[^>]*>\\s*([^<]{3,200})<");
            if (err == null) err = firstGroup(post.body(), "(?i)((?:invalid|incorrect|wrong|failed)[^<]{0,120})");
            throw new NikshayLoginRejectedException("Login rejected for " + device.getPhiUserId()
                    + (err != null ? ": " + err.trim() : ": did not reach " + env.homeUrl()), post.statusCode());
        }

        // 8. cookies for the portal origin → header
        List<HttpCookie> cookies = jar.getCookieStore().get(URI.create(env.getOrigin()));
        String cookieHeader = cookies.stream().map(c -> c.getName() + "=" + c.getValue()).collect(Collectors.joining("; "));
        boolean hasJwt = cookies.stream().anyMatch(c -> c.getName().equals(env.getJwtCookieName()));
        if (!hasJwt) log.warn("Login for {} succeeded but cookie {} not present ({} cookies)", device.getPhiUserId(), env.getJwtCookieName(), cookies.size());

        NikshaySession session = NikshaySession.builder()
                .deviceConfigId(device.getId()).deviceId(device.getDeviceId()).phiUserId(device.getPhiUserId())
                .origin(env.getOrigin()).cookieHeader(cookieHeader).build();

        // 9. verify – an authenticated page must not bounce us back to SSO
        verifySession(session);
        log.info("Ni-kshay login OK for device {} ({}) – {} cookies", device.getDeviceId(), device.getPhiUserId(), cookies.size());
        return session;
    }

    private void verifySession(NikshaySession s) {
        HttpResponse<String> r = send(plainClient(), apiRequest(s, s.getOrigin() + "/Home").GET());
        int sc = r.statusCode();
        boolean redirected = sc >= 300 && sc < 400;
        if (sc == 401 || sc == 403 || redirected)
            throw new NikshayLoginRejectedException("Session verification failed for " + s.getPhiUserId()
                    + (redirected ? " (redirected to " + r.headers().firstValue("Location").orElse("?") + ")" : ""), sc);
    }

    /** Best-effort logout; cookies are discarded by the caller regardless. */
    public void logout(NikshaySession s) {
        if (s == null) return;
        try {
            send(plainClient(), apiRequest(s, props.active().getSsoBase() + "/v1/sso/logout").GET());  // VERIFY path
        } catch (Exception e) {
            log.debug("logout ignored: {}", e.getMessage());
        }
    }

    // ======================================================================
    //  PORTAL APIs
    // ======================================================================

    /** GET /Api/Patients/GetPossibleDuplicates – "no match" arrives as Success:false, not as an error. */
    public DuplicateCheckResult getPossibleDuplicates(NikshaySession s, DuplicateCheckRequest q) {
        String url = s.getOrigin() + "/Api/Patients/GetPossibleDuplicates?"
                + "PrimaryPhone=" + enc(q.getPrimaryPhone())
                + "&Gender=" + enc(q.getGender())
                + "&FirstName=" + enc(q.getFirstName())
                + "&LastName=" + enc(q.getLastName())
                + "&Age=" + enc(q.getAge() == null ? "" : String.valueOf(q.getAge()))
                + "&DateOfBirth=" + enc(q.getDateOfBirth());
        HttpResponse<String> r = send(plainClient(), apiRequest(s, url).GET());
        guardSession(r);
        NikshayApiResponse env = parse(r);
        List<DuplicateCandidateDto> hits = new ArrayList<>();
        if (env.getData() != null && env.getData().isArray()) {
            for (JsonNode n : env.getData()) {
                try { hits.add(om.treeToValue(n, DuplicateCandidateDto.class)); } catch (Exception e) { log.warn("dup hit parse: {}", e.getMessage()); }
            }
        }
        // Success:false + "No existing patients Mapped to mobile number" == empty list, not a failure
        return DuplicateCheckResult.builder().allHits(hits).rawBody(truncate(r.body())).httpStatus(r.statusCode()).build();
    }

    /** POST /api/patients/AddIndiaTB – new patient (parentEpisodeId=null) or new episode (parentEpisodeId set). */
    public AddIndiaTbResponse addIndiaTb(NikshaySession s, AddIndiaTbRequest req) {
        String json = toJson(req);
        HttpResponse<String> r = send(plainClient(), apiRequest(s, s.getOrigin() + "/api/patients/AddIndiaTB")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
        guardSession(r);
        NikshayApiResponse env = parse(r);
        if (!env.isSuccess() || env.getPatientId() == null) {
            String msg = env.errorText();
            throw new NikshayApiException("AddIndiaTB rejected: " + (msg != null ? msg : "HTTP " + r.statusCode() + " " + truncate(r.body(), 200)), r.statusCode());
        }
        return AddIndiaTbResponse.builder().patientId(env.getPatientId()).rawBody(truncate(r.body())).httpStatus(r.statusCode()).build();
    }

    // ======================================================================
    //  plumbing
    // ======================================================================

    private HttpClient plainClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)   // a redirect to SSO == expired session, we want to see it
                .connectTimeout(Duration.ofMillis(props.getHttp().getConnectTimeoutMs()))
                .build();
    }

    private HttpRequest.Builder apiRequest(NikshaySession s, String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(props.getHttp().getReadTimeoutMs()))
                .header("Accept", "*/*")
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Origin", s.getOrigin())
                .header("Referer", s.getOrigin() + "/")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("User-Agent", props.getHttp().getUserAgent())
                .header("Cookie", s.getCookieHeader());
    }

    private HttpResponse<String> send(HttpClient http, HttpRequest.Builder b) {
        HttpRequest req = b.build();
        long t0 = System.currentTimeMillis();
        try {
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("{} {} -> {} in {}ms", req.method(), req.uri(), r.statusCode(), System.currentTimeMillis() - t0);
            if (r.statusCode() >= 500) throw new NikshayUnavailableException("Ni-kshay HTTP " + r.statusCode() + " on " + req.uri().getPath(), r.statusCode());
            return r;
        } catch (IOException e) {
            throw new NikshayUnavailableException("Ni-kshay unreachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NikshayUnavailableException("interrupted", e);
        }
    }

    /** 401/403, or a 3xx pointing at the SSO host, means the cookie is no longer accepted. */
    private void guardSession(HttpResponse<String> r) {
        int sc = r.statusCode();
        if (sc == 401 || sc == 403) throw new NikshaySessionExpiredException("Session rejected (HTTP " + sc + ")", sc);
        if (sc >= 300 && sc < 400) {
            String loc = r.headers().firstValue("Location").orElse("");
            if (loc.contains("/sso/")) throw new NikshaySessionExpiredException("Redirected to SSO login", sc);
        }
        if (sc >= 400) throw new NikshayApiException("Ni-kshay HTTP " + sc + ": " + truncate(r.body(), 200), sc);
    }

    private NikshayApiResponse parse(HttpResponse<String> r) {
        try {
            return om.readValue(r.body(), NikshayApiResponse.class);
        } catch (Exception e) {
            throw new NikshayApiException("Unparseable Ni-kshay response (HTTP " + r.statusCode() + "): " + truncate(r.body(), 200), r.statusCode());
        }
    }

    private JsonNode getJson(HttpClient http, String url, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(props.getHttp().getReadTimeoutMs()))
                .header("Accept", "application/json")
                .header("User-Agent", props.getHttp().getUserAgent()).GET();
        headers.forEach(b::header);
        HttpResponse<String> r = send(http, b);
        try { return om.readTree(r.body()); }
        catch (Exception e) { throw new NikshayUnavailableException("SSO returned non-JSON from " + url, 500); }
    }

    private static String jsonData(JsonNode n, String field) {
        JsonNode v = n.path("data").path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static String sha256Hex(String in) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(in.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String firstGroup(String text, String regex) {
        if (text == null) return null;
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String randomCaptcha() {
        String a = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random r = new Random();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(a.charAt(r.nextInt(a.length())));
        return sb.toString();
    }

    private static String enc(String v) { return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8); }

    private String toJson(Object o) {
        try { return om.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public String truncate(String s) { return truncate(s, props.getHttp().getLogBodyMaxChars()); }
    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}