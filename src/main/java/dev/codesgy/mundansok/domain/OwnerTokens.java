package dev.codesgy.mundansok.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 등록한 사람에게 주는 소유자 토큰. 256비트 난수이고, DB에는 SHA-256만 남긴다 */
public final class OwnerTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BEARER = "Bearer ";

    private OwnerTokens() {}

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 시간 차로 값을 추측하지 못하게 상수 시간으로 비교한다 */
    static boolean matches(String token, String expectedHash) {
        if (token == null || token.isEmpty() || expectedHash == null) return false;
        return MessageDigest.isEqual(hash(token).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }

    /** "Authorization: Bearer <토큰>" 헤더에서 토큰을 꺼낸다. 없거나 형식이 다르면 null */
    public static String fromAuthorization(String header) {
        if (header == null || header.length() <= BEARER.length() || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        String token = header.substring(BEARER.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
