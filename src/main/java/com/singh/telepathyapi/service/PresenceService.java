package com.singh.telepathyapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String PRESENCE_PREFIX = "presence:";
    private static final int PRESENCE_TTL_SECONDS = 60;

    public void userOnline(String userId) {
        String key = PRESENCE_PREFIX + userId;
        Map<String, Object> presence = new HashMap<>();
        presence.put("status", "online");
        presence.put("lastSeen", System.currentTimeMillis());

        redisTemplate.opsForValue().set(key, presence, PRESENCE_TTL_SECONDS, TimeUnit.SECONDS);
        broadcastPresence(userId, "online");
        log.debug("User {} is now online", userId);
    }

    public void userOffline(String userId) {
        redisTemplate.delete(PRESENCE_PREFIX + userId);
        broadcastPresence(userId, "offline");
        log.debug("User {} is now offline", userId);
    }

    public boolean isUserOnline(String userId) {
        return redisTemplate.hasKey(PRESENCE_PREFIX + userId);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserPresence(String userId) {
        Map<String, Object> presence = (Map<String, Object>) redisTemplate.opsForValue().get(PRESENCE_PREFIX + userId);
        if (presence == null) return Map.of("status", "offline", "lastSeen", null);
        return presence;
    }

    private void broadcastPresence(String userId, String status) {
        Map<String, Object> presenceUpdate = new HashMap<>();
        presenceUpdate.put("userId", userId);
        presenceUpdate.put("status", status);
        presenceUpdate.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend(
                "/topic/presence/" + userId,
                (Object) presenceUpdate
        );
    }
}
