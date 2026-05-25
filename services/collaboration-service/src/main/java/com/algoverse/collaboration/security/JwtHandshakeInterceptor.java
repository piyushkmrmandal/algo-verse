package com.algoverse.collaboration.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        // Token can arrive as a query param ?token= (SockJS workaround for WS headers)
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("token=")) {
                    String token = part.substring(6);
                    try {
                        attributes.put("userId", jwtService.extractUserId(token));
                        attributes.put("role", jwtService.extractRole(token));
                    } catch (Exception e) {
                        log.warn("WS handshake — invalid token: {}", e.getMessage());
                    }
                    break;
                }
            }
        }
        return true; // always allow; auth is enforced in channel interceptor
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {}
}
