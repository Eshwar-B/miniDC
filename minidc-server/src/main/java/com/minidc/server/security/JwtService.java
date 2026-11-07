package com.minidc.server.security;

import com.minidc.common.enums.Role;
import com.minidc.server.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;

/**
 * Service for generating, parsing, and validating HS256 JSON Web Tokens.
 */
@Service
public class JwtService {

    private final AppProperties properties;
    private final Key key;

    public JwtService(AppProperties properties) {
        this.properties = properties;
        this.key = deriveKey(properties.jwtSecret());
    }

    private Key deriveKey(String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                secretBytes = digest.digest(secretBytes);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 not available", e);
            }
        }
        return Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(UUID userId, String email, Role role) {
        long durationMillis = (long) properties.jwtHours() * 3600 * 1000;
        Date now = new Date();
        Date expiry = new Date(now.getTime() + durationMillis);

        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId.toString())
                .claim("role", role.name())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public CurrentUser extractUser(String token) {
        Claims claims = parseAndValidate(token);
        String email = claims.getSubject();
        UUID userId = UUID.fromString(claims.get("userId", String.class));
        Role role = Role.valueOf(claims.get("role", String.class));
        return new CurrentUser(userId, email, role);
    }
}
