package com.smartcourier.userservice.security;

import com.smartcourier.userservice.config.JwtProperties;
import com.smartcourier.userservice.model.Role;
import com.smartcourier.userservice.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Stateless JWT utility service.
 * Signs tokens with HS256 using the base64-decoded bytes of jwt.secret-key.
 * Claims: sub = userId, role = role name, email = email.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(JwtProperties jwtProperties) {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecretKey());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = jwtProperties.getExpirationMs();
    }

    /**
     * Generate a signed HS256 JWT for the given user.
     * Claims: sub=userId, role=roleName, email=email.
     */
    public String generateToken(User user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    /** Extract the userId (sub claim) from a validated token. */
    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    /** Extract the Role enum from the role claim. */
    public Role extractRole(String token) {
        String roleName = parseClaims(token).get("role", String.class);
        return Role.valueOf(roleName);
    }

    /**
     * Return true if the token has a valid signature and is not expired.
     * Returns false (never throws) for any failure — including malformed tokens.
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
