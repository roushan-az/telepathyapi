package com.singh.telepathyapi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingService {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String userId, WebSocketSession session) {
        sessions.put(userId, session);
    }

    public void unregister(String userId) {
        sessions.remove(userId);
    }

    public void send(String toUser, String payload) throws IOException {
        WebSocketSession session = sessions.get(toUser);
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(payload));
        }
    }
}
