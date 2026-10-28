package com.saas.quote;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlMs;

    public record Parsed(Long userId, Long sessionId) {}

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));   // secret must be >= 32 chars
        this.ttlMs = ttlMinutes * 60_000;
    }

    /** sid links the token to a LoginSession row, so signing out (or deactivating the user) revokes it. */
    public String create(Long userId, Long sessionId, String portal) {
        return Jwts.builder().subject(String.valueOf(userId)).claim("sid", String.valueOf(sessionId)).claim("portal", portal)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(key).compact();
    }

    public Parsed parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new Parsed(Long.valueOf(c.getSubject()), Long.valueOf(String.valueOf(c.get("sid"))));
    }
}
