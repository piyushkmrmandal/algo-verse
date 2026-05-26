package com.algoverse.execution.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Stateless JWT authentication filter.
 *
 * <p>Validates RS256 JWTs signed by the auth-service using its RSA public key.
 * The public key is injected via the {@code JWT_PUBLIC_KEY} environment variable
 * (PEM-encoded, stripped of headers/footers, base64 only).
 *
 * <p>On successful validation the filter sets a fully authenticated
 * {@link UsernamePasswordAuthenticationToken} on the SecurityContext with:
 * <ul>
 *   <li>principal — userId (UUID string from the {@code sub} claim)</li>
 *   <li>credentials — null</li>
 *   <li>authorities — mapped from the {@code roles} claim</li>
 * </ul>
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final PublicKey publicKey;

    public JwtAuthenticationFilter(@Value("${jwt.public-key}") String publicKeyPem) {
        this.publicKey = parsePublicKey(publicKeyPem);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (publicKey == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(AUTHORIZATION_HEADER);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            List<String> roles = claims.get("roles", List.class);

            if (userId != null && !userId.isBlank()) {
                List<SimpleGrantedAuthority> authorities = roles == null
                        ? Collections.emptyList()
                        : roles.stream()
                                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                                .toList();

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            // Continue filter chain — SecurityConfig will reject unauthenticated requests.
        } catch (Exception e) {
            log.error("Unexpected error during JWT processing: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Extracts the authenticated userId from the SecurityContext.
     * Throws {@link IllegalStateException} when called outside an authenticated request.
     */
    public static UUID extractUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return UUID.fromString(principal.toString());
    }

    private static PublicKey parsePublicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            log.warn("jwt.public-key is not configured — JWT validation disabled (dev/test mode)");
            return null;
        }
        try {
            // Strip PEM headers/footers and whitespace
            String clean = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("\\n", "").replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(clean);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse RSA public key for JWT validation", e);
        }
    }
}
