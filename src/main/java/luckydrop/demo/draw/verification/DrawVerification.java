package luckydrop.demo.draw.verification;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Utilities for the public commit-reveal draw verification flow. */
public final class DrawVerification {

    public static final String ALGORITHM_VERSION = "WEIGHTED_HMAC_SHA256_V1";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private DrawVerification() {
    }

    public static Proof newProof() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String seed = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Proof(seed, sha256Hex(seed));
    }

    public static double unitInterval(String seed, Long drawId, Long userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(seed.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(("LuckyDrop:v1:draw=" + drawId + ":user=" + userId)
                    .getBytes(StandardCharsets.UTF_8));
            long value = ByteBuffer.wrap(bytes, 0, Long.BYTES).getLong() & Long.MAX_VALUE;
            return (value + 1.0d) / ((double) Long.MAX_VALUE + 2.0d);
        } catch (Exception e) {
            throw new IllegalStateException("검증용 난수를 생성할 수 없습니다.", e);
        }
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("검증 해시를 생성할 수 없습니다.", e);
        }
    }

    public record Proof(String seed, String hash) {
    }
}
