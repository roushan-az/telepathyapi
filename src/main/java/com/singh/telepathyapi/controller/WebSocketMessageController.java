package com.singh.telepathyapi.controller;

import com.singh.telepathyapi.service.MessagingService;
import com.singh.telepathyapi.service.PresenceService;
import com.singh.telepathyapi.service.WebRTCSignalingService;
import com.singh.telepathyapi.service.WebRTCSignalingService.SignalingMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * Modernized WebSocket Message Controller
 * Handles real-time communication using Java 21 Records and Virtual Threads.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketMessageController {

    private final MessagingService messagingService;
    private final WebRTCSignalingService webRTCService;
    private final PresenceService presenceService;

    /**
     * Handle end-to-end encrypted message relay
     */
    @MessageMapping("/message.send")
    public void sendMessage(@Payload Map<String, Object> message, Principal principal) {
        String senderId = principal.getName();
        String recipientId = (String) message.get("recipientId");
        String encryptedPayload = (String) message.get("encryptedPayload");

        messagingService.relayMessage(senderId, recipientId, encryptedPayload); //
    }

    /**
     * WebRTC Call Signaling - Initiate Offer
     */
    @MessageMapping("/webrtc.offer")
    public void handleOffer(@Payload Map<String, Object> data, Principal principal) {
        String callerId = principal.getName();
        String callId = (String) data.get("callId");
        String calleeId = (String) data.get("calleeId");
        Object offer = data.get("offer");

        webRTCService.sendOffer(callId, callerId, calleeId, offer);
    }

    /**
     * WebRTC Call Signaling - Respond with Answer
     */
    @MessageMapping("/webrtc.answer")
    public void handleAnswer(@Payload Map<String, Object> data, Principal principal) {
        String calleeId = principal.getName();
        String callId = (String) data.get("callId");
        Object answer = data.get("answer");

        webRTCService.sendAnswer(callId, calleeId, answer);
    }

    /**
     * Exchange ICE candidates for P2P connection
     */
    @MessageMapping("/webrtc.ice")
    public void handleIceCandidate(@Payload Map<String, Object> data, Principal principal) {
        String userId = principal.getName();
        String callId = (String) data.get("callId");
        String targetUserId = (String) data.get("targetUserId");
        Object candidate = data.get("candidate");

        webRTCService.sendIceCandidate(callId, userId, targetUserId, candidate);
    }

    @MessageMapping("/webrtc.end")
    public void handleCallEnd(@Payload Map<String, Object> data, Principal principal) {
        String userId = principal.getName();
        String callId = (String) data.get("callId");

        webRTCService.endCall(callId, userId);
    }

    @MessageMapping("/webrtc.reject")
    public void handleCallReject(@Payload Map<String, Object> data, Principal principal) {
        String callId = (String) data.get("callId");
        String reason = (String) data.get("reason");

        webRTCService.rejectCall(callId, reason);
    }

    /**
     * Presence heartbeat to keep the user session alive in Redis
     */
    @MessageMapping("/presence.heartbeat")
    public void handleHeartbeat(Principal principal) {
        presenceService.userOnline(principal.getName()); //
    }

    /**
     * Setup user presence and deliver missed messages upon connection
     */
    public void handleConnect(SimpMessageHeaderAccessor headerAccessor) {
        if (headerAccessor.getUser() != null) {
            String userId = headerAccessor.getUser().getName();
            presenceService.userOnline(userId); //
            messagingService.deliverQueuedMessages(userId); //
            log.info("User session established: {}", userId);
        }
    }

    /**
     * Cleanup presence data upon disconnection
     */
    public void handleDisconnect(SimpMessageHeaderAccessor headerAccessor) {
        if (headerAccessor.getUser() != null) {
            String userId = headerAccessor.getUser().getName();
            presenceService.userOffline(userId); //
            log.info("User session terminated: {}", userId);
        }
    }
}