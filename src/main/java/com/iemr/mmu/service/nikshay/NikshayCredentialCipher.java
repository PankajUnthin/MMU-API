package com.iemr.mmu.service.nikshay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM for m_nikshay_device_configuration.password.
 * Stored form: base64( 12-byte IV || ciphertext || 16-byte tag ).
 * Key: env var NIKSHAY_CRED_KEY (base64, 32 bytes). Never logged, never in properties files.
 */
@Component
public class NikshayCredentialCipher {

    private static final String ALG = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12, TAG_BITS = 128;
    private final SecretKey key;
    private final SecureRandom rng = new SecureRandom();

    public NikshayCredentialCipher(@Value("${NIKSHAY_CRED_KEY:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("NIKSHAY_CRED_KEY environment variable is not set");
        }
        byte[] k = Base64.getDecoder().decode(base64Key);
        if (k.length != 32) throw new IllegalStateException("NIKSHAY_CRED_KEY must decode to 32 bytes");
        this.key = new SecretKeySpec(k, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LEN];
            rng.nextBytes(iv);
            Cipher c = Cipher.getInstance(ALG);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] in = Base64.getDecoder().decode(stored);
            Cipher c = Cipher.getInstance(ALG);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_LEN));
            return new String(c.doFinal(in, IV_LEN, in.length - IV_LEN), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt failed (wrong key or corrupt value)", e);
        }
    }
}