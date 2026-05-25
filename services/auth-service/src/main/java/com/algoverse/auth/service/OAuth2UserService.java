package com.algoverse.auth.service;

import com.algoverse.auth.domain.model.User;
import com.algoverse.auth.domain.model.UserRole;
import com.algoverse.auth.domain.repository.UserRepository;
import com.algoverse.auth.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Handles OAuth2 user info loading for Google, GitHub, and LinkedIn.
 * Finds or creates a User entity from the OAuth2 provider attributes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email = extractEmail(registrationId, attributes);
        String displayName = extractDisplayName(registrationId, attributes);
        String providerId = extractProviderId(registrationId, attributes);

        if (!StringUtils.hasText(email)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_not_found"),
                    "Email not found from OAuth2 provider: " + registrationId
            );
        }

        User user = userRepository.findByProviderAndProviderId(registrationId, providerId)
                .or(() -> userRepository.findByEmail(email))
                .map(existing -> updateExistingUser(existing, registrationId, providerId, displayName))
                .orElseGet(() -> createNewUser(email, displayName, registrationId, providerId));

        log.info("OAuth2 login: userId={}, provider={}", user.getId(), registrationId);

        UserPrincipal principal = new UserPrincipal(user);
        principal.setAttributes(attributes);
        return principal;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String extractEmail(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "GOOGLE" -> (String) attributes.get("email");
            case "GITHUB" -> (String) attributes.get("email");
            case "LINKEDIN" -> (String) attributes.get("email");
            default -> (String) attributes.get("email");
        };
    }

    private String extractDisplayName(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "GOOGLE" -> (String) attributes.getOrDefault("name", "User");
            case "GITHUB" -> (String) attributes.getOrDefault("name",
                    attributes.getOrDefault("login", "User"));
            case "LINKEDIN" -> {
                String given = (String) attributes.getOrDefault("given_name", "");
                String family = (String) attributes.getOrDefault("family_name", "");
                yield (given + " " + family).trim();
            }
            default -> (String) attributes.getOrDefault("name", "User");
        };
    }

    private String extractProviderId(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "GOOGLE" -> (String) attributes.get("sub");
            case "GITHUB" -> String.valueOf(attributes.get("id"));
            case "LINKEDIN" -> (String) attributes.get("sub");
            default -> (String) attributes.get("id");
        };
    }

    private User updateExistingUser(User user, String provider, String providerId, String displayName) {
        if (!StringUtils.hasText(user.getProvider()) || "LOCAL".equals(user.getProvider())) {
            user.setProvider(provider);
            user.setProviderId(providerId);
        }
        // Optionally update displayName if empty
        if (!StringUtils.hasText(user.getDisplayName()) && StringUtils.hasText(displayName)) {
            user.setDisplayName(displayName);
        }
        return userRepository.save(user);
    }

    private User createNewUser(String email, String displayName, String provider, String providerId) {
        User newUser = User.builder()
                .email(email)
                .displayName(StringUtils.hasText(displayName) ? displayName : email.split("@")[0])
                .provider(provider)
                .providerId(providerId)
                .role(UserRole.USER)
                .isActive(true)
                .build();
        return userRepository.save(newUser);
    }
}
