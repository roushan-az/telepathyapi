package com.singh.telepathyapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * WebRTC Signaling Service
 *
 * Server only relays signaling messages.
 * Media NEVER passes through server.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebRTCSignalingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MessagingService messagingService;
    private final ObjectMapper objectMapper;

    @Value("${webrtc.stun.servers}")
    private String stunServers;

    @Value("${webrtc.turn.enabled}")
    private boolean turnEnabled;

    @Value("${webrtc.turn.urls:}")
    private String turnUrls;

    @Value("${webrtc.turn.username:}")
    private String turnUsername;

    @Value("${webrtc.turn.credential:}")
    private String turnCredential;

    private static final String CALL_SESSION_PREFIX = "webrtc:call:";
    private static final String ICE_CANDIDATE_PREFIX = "webrtc:ice:";
    private static final int SIGNALING_DATA_TTL_SECONDS = 300;


    /* ---------------- ICE CONFIG ---------------- */

    public Map<String, Object> getIceServers() {
        List<Map<String, Object>> iceServers = new ArrayList<>();

        for (String stunServer : stunServers.split(",")) {
            Map<String, Object> server = new HashMap<>();
            server.put("urls", stunServer.trim());
            iceServers.add(server);
        }

        if (turnEnabled && turnUrls != null && !turnUrls.isEmpty()) {
            Map<String, Object> turnServer = new HashMap<>();
            turnServer.put("urls", turnUrls);
            turnServer.put("username", turnUsername);
            turnServer.put("credential", turnCredential);
            iceServers.add(turnServer);
        }

        Map<String, Object> config = new HashMap<>();
        config.put("iceServers", iceServers);
        config.put("iceCandidatePoolSize", 10);
        return config;
    }

    /* ---------------- CALL FLOW ---------------- */

    public void sendOffer(String callId, String callerId, String calleeId, Map<String, Object> offer) throws IOException {
        String sessionKey = CALL_SESSION_PREFIX + callId;

        Map<String, Object> callSession = new HashMap<>();
        callSession.put("callId", callId);
        callSession.put("caller", callerId);
        callSession.put("callee", calleeId);
        callSession.put("status", "OFFERING");
        callSession.put("offer", offer);
        callSession.put("startTime", System.currentTimeMillis());

        redisTemplate.opsForValue().set(
                sessionKey,
                callSession,
                SIGNALING_DATA_TTL_SECONDS,
                TimeUnit.SECONDS
        );

        Map<String, Object> message = Map.of(
                "type", "OFFER",
                "callId", callId,
                "from", callerId,
                "offer", offer
        );

        messagingService.send(calleeId, objectMapper.writeValueAsString(message));
        log.info("WebRTC OFFER {} -> {}", callerId, calleeId);
    }

    public void sendAnswer(String callId, String calleeId, Map<String, Object> answer) throws IOException {
        String sessionKey = CALL_SESSION_PREFIX + callId;

        @SuppressWarnings("unchecked")
        Map<String, Object> session =
                (Map<String, Object>) redisTemplate.opsForValue().get(sessionKey);

        if (session == null) return;

        session.put("status", "CONNECTED");
        session.put("answer", answer);

        redisTemplate.opsForValue().set(
                sessionKey,
                session,
                SIGNALING_DATA_TTL_SECONDS,
                TimeUnit.SECONDS
        );

        String callerId = (String) session.get("caller");

        Map<String, Object> message = Map.of(
                "type", "ANSWER",
                "callId", callId,
                "from", calleeId,
                "answer", answer
        );

        messagingService.send(callerId,objectMapper.writeValueAsString(message));
        log.info("WebRTC ANSWER {} -> {}", calleeId, callerId);
    }

    public void sendIceCandidate(
            String callId,
            String fromUser,
            String toUser,
            Map<String, Object> candidate
    ) throws IOException {
        String iceKey = ICE_CANDIDATE_PREFIX + callId + ":" + UUID.randomUUID();

        redisTemplate.opsForValue().set(
                iceKey,
                candidate,
                SIGNALING_DATA_TTL_SECONDS,
                TimeUnit.SECONDS
        );

        Map<String, Object> message = Map.of(
                "type", "ICE_CANDIDATE",
                "callId", callId,
                "from", fromUser,
                "candidate", candidate
        );

        messagingService.send(toUser, objectMapper.writeValueAsString(message));
    }

    public void endCall(String callId, String endedBy) throws IOException {
        String sessionKey = CALL_SESSION_PREFIX + callId;

        @SuppressWarnings("unchecked")
        Map<String, Object> session =
                (Map<String, Object>) redisTemplate.opsForValue().get(sessionKey);

        if (session == null) return;

        String caller = (String) session.get("caller");
        String callee = (String) session.get("callee");
        String otherUser = endedBy.equals(caller) ? callee : caller;

        messagingService.send(otherUser, objectMapper.writeValueAsString(Map.of(
                "type", "CALL_ENDED",
                "callId", callId,
                "endedBy", endedBy
        )));

        redisTemplate.delete(sessionKey);
        redisTemplate.delete(redisTemplate.keys(ICE_CANDIDATE_PREFIX + callId + ":*"));
    }

    public void rejectCall(String callId, String calleeId, String reason) throws IOException {
        String sessionKey = CALL_SESSION_PREFIX + callId;

        @SuppressWarnings("unchecked")
        Map<String, Object> session =
                (Map<String, Object>) redisTemplate.opsForValue().get(sessionKey);

        if (session == null) return;

        String caller = (String) session.get("caller");

        messagingService.send(caller, objectMapper.writeValueAsString(Map.of(
                "type", "CALL_REJECTED",
                "callId", callId,
                "reason", reason != null ? reason : "Declined"
        )));

        redisTemplate.delete(sessionKey);
    }
}
