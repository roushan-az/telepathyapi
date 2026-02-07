package com.singh.telepathyapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Modernized WebRTC Signaling Service for Spring Boot 4 & Java 21.
 * Uses Records for type safety and Redis for session persistence.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebRTCSignalingService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${webrtc.stun.servers:stun:stun.l.google.com:19302}")
    private String stunServers;

    @Value("${webrtc.turn.enabled:false}")
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

    /**
     * Strongly typed signaling messages using Java 21 Records
     */
    public record SignalingMessage(
            String type,
            String callId,
            String from,
            Object payload, // Can be SDP or ICE candidate
            long timestamp
    ) {}

    public record CallSession(
            String callId,
            String caller,
            String callee,
            String status,
            Object offer,
            long startTime
    ) {}

    /**
     * Generates ICE Server configuration.
     *      */
    public Map<String, Object> getIceServers() {
        List<Map<String, Object>> iceServers = new ArrayList<>();

        Arrays.stream(stunServers.split(","))
                .map(String::trim)
                .forEach(url -> iceServers.add(Map.of("urls", url)));

        if (turnEnabled && !turnUrls.isBlank()) {
            iceServers.add(Map.of(
                    "urls", turnUrls,
                    "username", turnUsername,
                    "credential", turnCredential
            ));
        }

        return Map.of(
                "iceServers", iceServers,
                "iceCandidatePoolSize", 10,
                "bundlePolicy", "max-bundle"
        );
    }

    public void sendOffer(String callId, String callerId, String calleeId, Object offer) {
        var session = new CallSession(callId, callerId, calleeId, "OFFERING", offer, System.currentTimeMillis());

        redisTemplate.opsForValue().set(
                CALL_SESSION_PREFIX + callId,
                session,
                SIGNALING_DATA_TTL_SECONDS,
                TimeUnit.SECONDS
        );

        dispatch(calleeId, new SignalingMessage("OFFER", callId, callerId, offer, System.currentTimeMillis()));
        log.info("RTC Offer: {} -> {} (ID: {})", callerId, calleeId, callId);
    }

    public void sendAnswer(String callId, String calleeId, Object answer) {
        var session = getCallSession(callId);
        if (session == null) {
            log.warn("Attempted to answer expired/non-existent call: {}", callId);
            return;
        }

        // Update session status to CONNECTED
        var updatedSession = new CallSession(callId, session.caller(), calleeId, "CONNECTED", session.offer(), session.startTime());
        redisTemplate.opsForValue().set(CALL_SESSION_PREFIX + callId, updatedSession, SIGNALING_DATA_TTL_SECONDS, TimeUnit.SECONDS);

        dispatch(session.caller(), new SignalingMessage("ANSWER", callId, calleeId, answer, System.currentTimeMillis()));
    }

    public void sendIceCandidate(String callId, String userId, String targetUserId, Object candidate) {
        // We don't necessarily need to store every ICE candidate in Redis if the peer is online,
        // but we cache it briefly in case of momentary flap.
        String iceKey = ICE_CANDIDATE_PREFIX + callId + ":" + userId + ":" + UUID.randomUUID();
        redisTemplate.opsForValue().set(iceKey, candidate, 60, TimeUnit.SECONDS);

        dispatch(targetUserId, new SignalingMessage("ICE_CANDIDATE", callId, userId, candidate, System.currentTimeMillis()));
    }

    public void endCall(String callId, String userId) {
        var session = getCallSession(callId);
        if (session != null) {
            String peerId = userId.equals(session.caller()) ? session.callee() : session.caller();

            dispatch(peerId, new SignalingMessage("CALL_ENDED", callId, userId, null, System.currentTimeMillis()));

            redisTemplate.delete(CALL_SESSION_PREFIX + callId);
            cleanIceCandidates(callId);
            log.info("Call {} terminated by {}", callId, userId);
        }
    }

    public void rejectCall(String callId, String reason) {
        var session = getCallSession(callId);
        if (session != null) {
            dispatch(session.caller(), new SignalingMessage("CALL_REJECTED", callId, session.callee(), reason, System.currentTimeMillis()));
            redisTemplate.delete(CALL_SESSION_PREFIX + callId);
        }
    }

    private CallSession getCallSession(String callId) {
        return (CallSession) redisTemplate.opsForValue().get(CALL_SESSION_PREFIX + callId);
    }

    private void dispatch(String destinationUser, SignalingMessage message) {
        messagingTemplate.convertAndSendToUser(destinationUser, "/queue/webrtc", message);
    }

    private void cleanIceCandidates(String callId) {
        Set<String> keys = redisTemplate.keys(ICE_CANDIDATE_PREFIX + callId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}