package com.algoverse.auth.infrastructure.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {

    private String privateKey;
    private String publicKey;
    private long accessTokenExpiry;
    private long refreshTokenExpiry;
}
