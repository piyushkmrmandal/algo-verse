package com.algoverse.submission.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Stateless JWT RS256 security configuration.
 *
 * <p>Extracts the user UUID from the JWT {@code sub} claim and sets it as the
 * Spring Security principal so controllers can inject it via
 * {@code @AuthenticationPrincipal UUID userId}.
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    @Value("${jwt.public-key:}")
    private String jwtPublicKey;

    // Paths that do not require authentication
    private static final String[] PUBLIC_PATHS = {
            "/actuator/**",
            "/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/ws/**"          // WebSocket handshake
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtPublicKey);
    }

    // ── Inner filter ──────────────────────────────────────────────────────────

    public static class JwtAuthFilter extends OncePerRequestFilter {

        private final String rawPublicKey;

        public JwtAuthFilter(String rawPublicKey) {
            this.rawPublicKey = rawPublicKey;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain)
                throws ServletException, IOException {

            String header = request.getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                chain.doFilter(request, response);
                return;
            }

            String token = header.substring(7);
            try {
                DecodedJWT decoded = verify(token);
                String subject = decoded.getSubject();
                UUID userId = UUID.fromString(subject);

                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_USER"));

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("JwtAuthFilter: authenticated userId={}", userId);

            } catch (JWTVerificationException | IllegalArgumentException ex) {
                log.warn("JwtAuthFilter: invalid token — {}", ex.getMessage());
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT");
                return;
            }

            chain.doFilter(request, response);
        }

        private DecodedJWT verify(String token) throws JWTVerificationException {
            try {
                if (rawPublicKey == null || rawPublicKey.isBlank()) {
                    // Dev/test fallback: decode without signature verification
                    log.warn("JwtAuthFilter: no public key configured — skipping signature verification (dev mode)");
                    return JWT.decode(token);
                }

                String cleaned = rawPublicKey
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s+", "");

                byte[] keyBytes = Base64.getDecoder().decode(cleaned);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                RSAPublicKey publicKey = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(keyBytes));

                Algorithm algorithm = Algorithm.RSA256(publicKey, null);
                JWTVerifier verifier = JWT.require(algorithm).build();
                return verifier.verify(token);

            } catch (JWTVerificationException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new JWTVerificationException("Key parsing failed: " + ex.getMessage(), ex);
            }
        }
    }
}
