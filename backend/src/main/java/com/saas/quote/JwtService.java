package com.saas.quote;

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

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));   // secret must be >= 32 chars
        this.ttlMs = ttlMinutes * 60_000;
    }

    public String create(Long userId, String email) {
        return Jwts.builder().subject(String.valueOf(userId)).claim("email", email)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(key).compact();
    }

    public Long parse(String token) {
        return Long.valueOf(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject());
    }
}
