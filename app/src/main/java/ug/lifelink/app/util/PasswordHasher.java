package ug.lifelink.app.util;

import android.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing with PBKDF2-HMAC-SHA256.
 *
 * Why hash and not encrypt: encryption can be reversed with the key, hashing cannot.
 * Why a salt: two users with the same password get different hashes, and precomputed
 * tables become useless. Why 100,000 iterations: each guess an attacker makes is slow,
 * but one login is still fast enough for a phone.
 *
 * PBKDF2WithHmacSHA256 needs Android 8.0 (API 26), which is the app's minimum SDK.
 * Hashing takes noticeable time, so callers run it off the main thread.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 100_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private PasswordHasher() {
    }

    /** A fresh random salt, Base64 encoded for storage in a TEXT column. */
    public static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    public static String hash(String password, String saltBase64) {
        byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Password hashing is unavailable on this device", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Compares in constant time so the comparison itself does not leak information. */
    public static boolean verify(String password, String saltBase64, String expectedHashBase64) {
        byte[] actual = Base64.decode(hash(password, saltBase64), Base64.NO_WRAP);
        byte[] expected = Base64.decode(expectedHashBase64, Base64.NO_WRAP);
        return MessageDigest.isEqual(actual, expected);
    }
}
