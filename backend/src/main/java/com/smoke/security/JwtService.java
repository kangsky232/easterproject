package com.smoke.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMillis) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt.secret 至少需要 32 字节");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String createToken(UserAccountPrincipal principal, String passwordHash) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.username())
                .claim("role", principal.role())
                .claim("credential_fingerprint", credentialFingerprint(passwordHash))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * The token carries an HMAC of the current stored password hash, never the hash itself.
     * A password change therefore makes every previously issued token fail validation.
     */
    public boolean credentialMatches(Claims claims, String passwordHash) {
        String tokenFingerprint = claims.get("credential_fingerprint", String.class);
        if (tokenFingerprint == null || passwordHash == null) {
            return false;
        }
        byte[] expected = credentialFingerprint(passwordHash).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = tokenFingerprint.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private String credentialFingerprint(String passwordHash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            byte[] fingerprint = mac.doFinal(passwordHash.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(fingerprint);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to derive JWT credential fingerprint", exception);
        }
    }
}
