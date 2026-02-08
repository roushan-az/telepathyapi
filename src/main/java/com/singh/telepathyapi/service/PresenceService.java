package com.singh.telepathyapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class PresenceService {

    private final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();
    private final MessagingService messagingService;

    public PresenceService(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    public void userOnline(String userId) {
        onlineUsers.add(userId);
        broadcastPresence(userId, true);
    }

    public void userOffline(String userId) {
        onlineUsers.remove(userId);
        broadcastPresence(userId, false);
    }

    public boolean isOnline(String userId) {
        return onlineUsers.contains(userId);
    }

    private void broadcastPresence(String userId, boolean online) {
        // OPTIONAL: encrypted presence message
        // Server still does not inspect payload semantics
        try {
            String payload = """
                {
                  "type": "PRESENCE",
                  "user": "%s",
                  "online": %s
                }
                """.formatted(userId, online);

            // In real Signal-style systems, this is usually opt-in
            // or fetched on demand rather than broadcast
            messagingService.send(userId, payload);

        } catch (Exception ignored) {
            // Presence failure should never crash the server
        }
    }
}
