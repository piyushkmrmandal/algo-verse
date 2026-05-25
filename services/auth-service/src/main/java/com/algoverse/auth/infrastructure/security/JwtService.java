package com.algoverse.auth.infrastructure.security;

import com.algoverse.auth.domain.model.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String BLOCKLIST_PREFIX = "blocklist:jti:";
    private static final String REFRESH_PREFIX = "refresh:";

    private final JwtProperties jwtProperties;
    private final RedisTemplate<String, String> redisTemplate;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private Algorithm algorithm;
    private JWTVerifier verifier;

    @PostConstruct
    public void init() {
        try {
            String privateKeyPem = jwtProperties.getPrivateKey();
            String publicKeyPem = jwtProperties.getPublicKey();

            if (StringUtils.hasText(privateKeyPem) && StringUtils.hasText(publicKeyPem)) {
                this.privateKey = (RSAPrivateKey) loadPrivateKey(privateKeyPem);
                this.publicKey = (RSAPublicKey) loadPublicKey(publicKeyPem);
                log.info("JWT RS256 keys loaded from environment variables");
            } else {
                log.warn("JWT_PRIVATE_KEY / JWT_PUBLIC_KEY not set — generating ephemeral 2048-bit RSA key pair for local dev");
                KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
                kpg.initialize(2048);
                KeyPair kp = kpg.generateKeyPair();
                this.privateKey = (RSAPrivateKey) kp.getPrivate();
                this.publicKey = (RSAPublicKey) kp.getPublic();
            }

            this.algorithm = Algorithm.RSA256(this.publicKey, this.privateKey);
            this.verifier = JWT.require(algorithm).build();
            log.info("JWT service initialized with RS256 algorithm");
        } catch (Exception e) {
            log.error("Failed to initialize JWT service", e);
            throw new IllegalStateException("Cannot initialize JWT keys", e);
        }
    }

    /**
     * Generates a signed RS256 access token with a 15-minute expiry.
     * Claims: sub=userId, email, role, displayName, jti (random UUID)
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.getAccessTokenExpiry());

        return JWT.create()
                .withJWTId(UUID.randomUUID().toString())
                .withSubject(user.getId().toString())
                .withClaim("email", user.getEmail())
                .withClaim("role", user.getRole().name())
                .withClaim("displayName", user.getDisplayName())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .sign(algorithm);
    }

    /**
     * Generates an opaque refresh token (UUID), stores it in Redis with 7d TTL.
     * Key pattern: refresh:{userId}:{tokenId}
     */
    public String generateRefreshToken(User user) {
        String tokenId = UUID.randomUUID().toString();
        String redisKey = REFRESH_PREFIX + user.getId() + ":" + tokenId;
        redisTemplate.opsForValue().set(
                redisKey,
                user.getId().toString(),
                jwtProperties.getRefreshTokenExpiry(),
                TimeUnit.SECONDS
        );
        // Return the composite token so the server can look it up
        return tokenId;
    }

    /**
     * Validates and returns the decoded JWT. Throws JWTVerificationException on failure.
     */
    public DecodedJWT validateToken(String token) throws JWTVerificationException {
        return verifier.verify(token);
    }

    /**
     * Blacklists a JWT by its jti claim in Redis until the token's natural expiry.
     */
    public void blacklistToken(String jti, Instant expiresAt) {
        long ttlSeconds = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue().set(
                    BLOCKLIST_PREFIX + jti,
                    "revoked",
                    ttlSeconds,
                    TimeUnit.SECONDS
            );
        }
    }

    /**
     * Validates a refresh token against Redis. Returns userId if valid, null otherwise.
     */
    public String validateRefreshToken(String userId, String tokenId) {
        String redisKey = REFRESH_PREFIX + userId + ":" + tokenId;
        return redisTemplate.opsForValue().get(redisKey);
    }

    /**
     * Deletes a refresh token from Redis (used during rotation or logout).
     */
    public void deleteRefreshToken(String userId, String tokenId) {
        String redisKey = REFRESH_PREFIX + userId + ":" + tokenId;
        redisTemplate.delete(redisKey);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLOCKLIST_PREFIX + jti));
    }

    public UUID extractUserId(DecodedJWT jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    public long getAccessTokenExpiry() {
        return jwtProperties.getAccessTokenExpiry();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private PrivateKey loadPrivateKey(String pem) throws Exception {
        String stripped = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(stripped);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private PublicKey loadPublicKey(String pem) throws Exception {
        String stripped = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(stripped);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
