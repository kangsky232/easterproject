package com.smoke.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 设备令牌的生成、摘要和常量时间比对。数据库中不保存明文令牌。
 */
public final class DeviceCredentialCodec {

    private static final SecureRandom RANDOM = new SecureRandom();

    private DeviceCredentialCodec() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        return Base64.getEncoder().encodeToString(digest(token));
    }

    public static boolean matches(String token, String expectedHash) {
        if (token == null || token.isBlank() || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        try {
            return MessageDigest.isEqual(digest(token), Base64.getDecoder().decode(expectedHash));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
