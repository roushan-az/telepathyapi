package com.singh.telepathyapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocket Configuration
 * 
 * Used for:
 * - Real-time message delivery (encrypted payloads only)
 * - WebRTC signaling (SDP, ICE candidates)
 * - Typing indicators
 * - Presence updates
 * 
 * Note: Message content is encrypted client-side before sending
 * Server only relays encrypted packets - cannot read content
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${websocket.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory broker for message routing
        config.enableSimpleBroker("/topic", "/queue", "/user");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
            .setMessageSizeLimit(65536) // 64KB max message size
            .setSendBufferSizeLimit(512 * 1024) // 512KB send buffer
            .setSendTimeLimit(20 * 1000); // 20 seconds timeout
    }
}
