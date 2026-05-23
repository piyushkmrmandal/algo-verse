package com.algoverse.execution.infrastructure.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over SockJS WebSocket configuration.
 *
 * <p>Clients connect to {@code /ws} with SockJS fallback, then subscribe to
 * {@code /user/{userId}/topic/submission} for real-time submission updates.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${CORS_ORIGINS:http://localhost:5173}")
    private String corsOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(corsOrigins.split(","))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable in-memory broker for topic and user-specific destinations.
        registry.enableSimpleBroker("/topic", "/user");

        // Prefix for messages routed to @MessageMapping methods (if any).
        registry.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific destinations (personalised subscriptions).
        registry.setUserDestinationPrefix("/user");
    }
}
