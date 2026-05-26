package com.algoverse.collaboration.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Service
public class JwtService {

    @Value("${jwt.public-key:}")
    private String publicKeyPem;

    private Algorithm algorithm;

    @PostConstruct
    void init() {
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            log.warn("jwt.public-key not set — JWT verification disabled (dev mode)");
            return;
        }
        try {
            String stripped = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("\\n", "").replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(stripped);
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decoded));
            algorithm = Algorithm.RSA256(key, null);
        } catch (Exception e) {
            log.error("Failed to load JWT public key: {}", e.getMessage());
        }
    }

    public String extractUserId(String token) {
        return decode(token).getSubject();
    }

    public String extractRole(String token) {
        String role = decode(token).getClaim("role").asString();
        return role != null ? role : "USER";
    }

    private DecodedJWT decode(String token) {
        if (algorithm == null) {
            return JWT.decode(token);
        }
        return JWT.require(algorithm).build().verify(token);
    }
}
