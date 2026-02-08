package com.singh.telepathyapi.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

@Component
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtHandshakeInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        try {
            log.info("🔍 WebSocket handshake attempt from: {}", request.getRemoteAddress());
            log.info("📍 Request URI: {}", request.getURI());

            String token = extractTokenFromQuery(request.getURI());

            if (token == null) {
                log.warn("❌ WebSocket handshake REJECTED: No token found in query parameters");
                log.warn("   Query string: {}", request.getURI().getQuery());
                return false;
            }

            log.info("🔑 Token found, validating...");

            if (!jwtTokenProvider.validateToken(token)) {
                log.warn("❌ WebSocket handshake REJECTED: Invalid token");
                log.warn("   Token (first 20 chars): {}...", token.substring(0, Math.min(20, token.length())));
                return false;
            }

            String userId = jwtTokenProvider.getUserIdFromToken(token);

            if (userId == null || userId.isEmpty()) {
                log.warn("❌ WebSocket handshake REJECTED: Could not extract userId from token");
                return false;
            }

            attributes.put("userId", userId);

            log.info("✅ WebSocket handshake ACCEPTED for user: {}", userId);
            return true;

        } catch (Exception e) {
            log.error("❌ WebSocket handshake FAILED with exception: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        if (exception != null) {
            log.error("❌ WebSocket handshake error: {}", exception.getMessage(), exception);
        } else {
            log.info("✅ WebSocket handshake completed successfully");
        }
    }

    private String extractTokenFromQuery(URI uri) {
        if (uri.getQuery() == null) {
            log.warn("⚠️ No query string found in URI: {}", uri);
            return null;
        }

        log.debug("🔍 Parsing query string: {}", uri.getQuery());

        for (String param : uri.getQuery().split("&")) {
            if (param.startsWith("token=")) {
                String token = param.substring(6);
                log.debug("✅ Token found in query parameters");
                return token;
            }
        }

        log.warn("⚠️ 'token' parameter not found in query string");
        return null;
    }
}