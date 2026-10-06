package com.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** API 시크릿 키를 SHA-256 hex 문자열로 변환한다. */
public final class ApiKeyHasher {

    private ApiKeyHasher() {
    }

    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JVM에서 필수 지원 알고리즘이라 여기에 도달하지 않는다.
            throw new IllegalStateException(e);
        }
    }

    /** 타이밍 공격을 피하기 위한 상수 시간 비교. */
    public static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
