package come.ying.lifenote.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilsTest {

    private static final String SECRET = "unit-test-secret-key-0123456789abcdef0123456789";
    private static final long TTL = 60_000;

    @Test
    void roundTripKeepsClaims() {
        UUID userId = UUID.randomUUID();
        String token = JwtUtils.createJwt(SECRET, TTL,
                Map.of("userId", userId.toString(), "username", "tester"));

        Claims claims = JwtUtils.parseJwt(SECRET, token);

        assertEquals(userId.toString(), claims.get("userId"));
        assertEquals("tester", claims.get("username"));
        assertTrue(claims.getExpiration().getTime() > System.currentTimeMillis());
    }

    @Test
    void expiredTokenIsRejected() {
        String token = JwtUtils.createJwt(SECRET, -5_000, Map.of("userId", "someone"));

        assertThrows(ExpiredJwtException.class, () -> JwtUtils.parseJwt(SECRET, token));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = JwtUtils.createJwt(SECRET, TTL, Map.of("userId", "someone"));
        String otherSecret = "a-completely-different-secret-key-9876543210";

        assertThrows(SignatureException.class, () -> JwtUtils.parseJwt(otherSecret, token));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = JwtUtils.createJwt(SECRET, TTL, Map.of("userId", "someone"));
        int last = token.length() - 1;
        char flipped = token.charAt(last) == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, last) + flipped;

        assertThrows(SignatureException.class, () -> JwtUtils.parseJwt(SECRET, tampered));
    }
}
