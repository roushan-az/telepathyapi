package com.singh.telepathyapi.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingService {

    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;

    private final Map<String, List<EncryptedMessage>> offlineMessageQueue = new HashMap<>();
    private static final int MAX_QUEUE_SIZE = 100;
    private static final long MESSAGE_TIMEOUT_MS = 5 * 60 * 1000; // 5 min

    public void relayMessage(String senderId, String recipientId, String encryptedPayload) {
        EncryptedMessage message = EncryptedMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .senderId(senderId)
                .recipientId(recipientId)
                .encryptedPayload(encryptedPayload)
                .timestamp(System.currentTimeMillis())
                .build();

        boolean delivered = attemptDelivery(message);

        if (!delivered) queueOfflineMessage(recipientId, message);
        sendDeliveryReceipt(senderId, message.getMessageId(), delivered ? "delivered" : "queued");
    }

    private boolean attemptDelivery(EncryptedMessage message) {
        if (!presenceService.isUserOnline(message.getRecipientId())) return false;

        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", message.getMessageId());
        payload.put("from", message.getSenderId());
        payload.put("encryptedPayload", message.getEncryptedPayload());
        payload.put("timestamp", message.getTimestamp());

        try {
            messagingTemplate.convertAndSendToUser(
                    message.getRecipientId(),
                    "/queue/messages",
                    payload
            );
            return true;
        } catch (Exception e) {
            log.error("Failed to deliver message: {}", e.getMessage());
            return false;
        }
    }

    private void queueOfflineMessage(String userId, EncryptedMessage message) {
        synchronized (offlineMessageQueue) {
            List<EncryptedMessage> queue = offlineMessageQueue.computeIfAbsent(userId, k -> new ArrayList<>());
            queue.removeIf(msg -> System.currentTimeMillis() - msg.getTimestamp() > MESSAGE_TIMEOUT_MS);
            if (queue.size() >= MAX_QUEUE_SIZE) queue.remove(0);
            queue.add(message);
        }
    }

    public void deliverQueuedMessages(String userId) {
        List<EncryptedMessage> messages;
        synchronized (offlineMessageQueue) { messages = offlineMessageQueue.remove(userId); }
        if (messages != null) messages.forEach(this::attemptDelivery);
    }

    private void sendDeliveryReceipt(String senderId, String messageId, String status) {
        Map<String, Object> receipt = Map.of(
                "messageId", messageId,
                "status", status,
                "timestamp", System.currentTimeMillis()
        );
        messagingTemplate.convertAndSendToUser(senderId, "/queue/receipts", receipt);
    }

    @Data
    @Builder
    private static class EncryptedMessage {
        private String messageId;
        private String senderId;
        private String recipientId;
        private String encryptedPayload;
        private long timestamp;
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredMessages() {
        synchronized (offlineMessageQueue) {
            offlineMessageQueue.values().forEach(queue ->
                    queue.removeIf(msg -> System.currentTimeMillis() - msg.getTimestamp() > MESSAGE_TIMEOUT_MS));
            offlineMessageQueue.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
    }
}
