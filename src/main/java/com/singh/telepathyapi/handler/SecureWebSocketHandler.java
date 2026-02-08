package com.singh.telepathyapi.handler;

import com.singh.telepathyapi.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SecureWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecureWebSocketHandler(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object userId = session.getAttributes().get("userId");

        if (userId == null) {
            log.warn("WebSocket connection rejected: missing userId");
            session.close();
            return;
        }

        sessions.put(userId.toString(), session);
        log.info("WebSocket connected: {}", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {

        try {
            // Parse the incoming message
            JsonNode payload = objectMapper.readTree(message.getPayload());

            // Extract sender info
            Object senderUserId = session.getAttributes().get("userId");
            String from = senderUserId != null ? senderUserId.toString() : null;

            // Get recipient - check multiple possible fields
            String to = extractRecipient(payload);

            if (to == null || to.isEmpty()) {
                log.warn("Message received without valid 'to' field from user: {}", from);
                sendErrorToSender(session, "Missing recipient ('to' field)");
                return;
            }

            // Get the target session
            WebSocketSession target = sessions.get(to);

            if (target != null && target.isOpen()) {
                // Add 'from' field to the message if not present
                JsonNode enrichedPayload = enrichMessageWithSender(payload, from);

                // Send to recipient
                String enrichedMessage = objectMapper.writeValueAsString(enrichedPayload);
                target.sendMessage(new TextMessage(enrichedMessage));

                log.debug("Message forwarded from {} to {}: type={}",
                        from, to, payload.has("type") ? payload.get("type").asText() : "unknown");
            } else {
                log.warn("Target user {} not connected or session closed", to);
                sendErrorToSender(session, "Recipient not available");
            }

        } catch (Exception e) {
            log.error("Error handling WebSocket message: {}", e.getMessage(), e);
            sendErrorToSender(session, "Failed to process message");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userId = session.getAttributes().get("userId");
        if (userId != null) {
            sessions.remove(userId.toString());
            log.info("WebSocket disconnected: {} - Status: {}", userId, status);
        }
    }

    /**
     * Extract recipient from various possible fields in the payload
     */
    private String extractRecipient(JsonNode payload) {
        // Try 'to' field first
        if (payload.has("to") && !payload.get("to").isNull()) {
            return payload.get("to").asText();
        }

        // Try 'recipientId' field
        if (payload.has("recipientId") && !payload.get("recipientId").isNull()) {
            return payload.get("recipientId").asText();
        }

        // Try 'peerId' field (for WebRTC messages)
        if (payload.has("peerId") && !payload.get("peerId").isNull()) {
            return payload.get("peerId").asText();
        }

        // Try nested 'payload.to' field
        if (payload.has("payload") && payload.get("payload").has("to")
                && !payload.get("payload").get("to").isNull()) {
            return payload.get("payload").get("to").asText();
        }

        return null;
    }

    /**
     * Add 'from' field to message if not already present
     */
    private JsonNode enrichMessageWithSender(JsonNode payload, String from) {
        if (from == null) {
            return payload;
        }

        try {
            // Convert to mutable map
            Map<String, Object> messageMap = objectMapper.convertValue(payload, Map.class);

            // Add 'from' if not present
            if (!messageMap.containsKey("from")) {
                messageMap.put("from", from);
            }

            // Convert back to JsonNode
            return objectMapper.valueToTree(messageMap);
        } catch (Exception e) {
            log.warn("Failed to enrich message with sender info: {}", e.getMessage());
            return payload;
        }
    }

    /**
     * Send error message back to sender
     */
    private void sendErrorToSender(WebSocketSession session, String errorMessage) {
        try {
            Map<String, Object> error = Map.of(
                    "type", "ERROR",
                    "message", errorMessage,
                    "timestamp", System.currentTimeMillis()
            );

            String errorJson = objectMapper.writeValueAsString(error);
            session.sendMessage(new TextMessage(errorJson));
        } catch (Exception e) {
            log.error("Failed to send error message: {}", e.getMessage());
        }
    }

    /**
     * Get JWT token from WebSocket session URI query parameters
     */
    private String getTokenFromSession(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return null;

        for (String param : uri.getQuery().split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }

    /**
     * Get count of active connections
     */
    public int getActiveConnectionCount() {
        return sessions.size();
    }

    /**
     * Check if a user is connected
     */
    public boolean isUserConnected(String userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }
}