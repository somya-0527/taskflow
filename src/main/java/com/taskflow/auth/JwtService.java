package com.taskflow.auth;

import com.taskflow.config.JwtProperties;
import com.taskflow.user.Role;
import com.taskflow.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService{
    private final SecretKey key;
    private final long expirationMs;

    public JwtService(JwtProperties properties) {
    this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    this.expirationMs = properties.expirationMs();
    }

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))          // "sub": who this token is about
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(now)                                    // "iat"
                .expiration(new Date(now.getTime() + expirationMs)) // "exp"
                .signWith(key)                                    // HMAC-SHA256 signature
                .compact();
    }

    /** Throws JwtException if the token is tampered with, malformed, or expired. */
    public AuthenticatedUser parseToken(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new AuthenticatedUser(
                Long.valueOf(claims.getSubject()),
                claims.get("email", String.class),
                Role.valueOf(claims.get("role", String.class)));
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

}