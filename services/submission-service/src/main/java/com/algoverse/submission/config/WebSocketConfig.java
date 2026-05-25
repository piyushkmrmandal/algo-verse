package com.algoverse.submission.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over SockJS WebSocket configuration.
 *
 * <p>Clients connect to {@code /ws} and subscribe to topics under
 * {@code /topic/submissions/{submissionId}} to receive real-time grading results.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory broker for /topic destinations
        config.enableSimpleBroker("/topic");
        // Prefix for client-to-server messages handled by @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        "http://localhost:5173",   // Vite dev server
                        "http://localhost:3000",   // alternative local
                        "https://*.algoverse.io"   // production domains
                )
                .withSockJS();
    }
}
