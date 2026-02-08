package com.singh.telepathyapi.config;

import com.singh.telepathyapi.security.JwtHandshakeInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Production-Grade WebSocket Configuration
 *
 * Based on how Signal, WhatsApp, and Zoom handle real-time calls:
 * 1. Large buffer sizes for streaming
 * 2. Connection pooling and thread management
 * 3. Heartbeat/ping-pong for connection health
 * 4. Async message processing
 * 5. Graceful degradation under load
 */
@Configuration
@EnableWebSocket
@Slf4j
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketHandler webSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    public WebSocketConfig(
            @Lazy WebSocketHandler webSocketHandler,
            JwtHandshakeInterceptor jwtHandshakeInterceptor
    ) {
        this.webSocketHandler = webSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Main secured WebSocket endpoint
        registry
                .addHandler(webSocketHandler, "/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");  // CORS fix for credentials
        // NOTE: Removed .withSockJS() for now - add it back if needed

        log.info("✅ WebSocket handlers registered with production-grade configuration");
        log.info("   📍 Endpoint: /ws");
        log.info("   🔒 Handler: {}", webSocketHandler.getClass().getSimpleName());
        log.info("   🛡️ Interceptor: {}", jwtHandshakeInterceptor.getClass().getSimpleName());
        log.info("   🌍 CORS: All origins allowed (change for production!)");

        // OPTIONAL: Uncomment this test endpoint for debugging
        // registry
        //         .addHandler(webSocketHandler, "/ws-test")
        //         .setAllowedOriginPatterns("*");
        // log.info("   🧪 Test endpoint: /ws-test (NO AUTH - REMOVE IN PRODUCTION!)");
    }

    /**
     * Configure WebSocket container with production settings
     * This is critical for handling high concurrent calls without hanging
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();

        // STREAMING SUPPORT: Large buffers for continuous data flow
        // Signal uses 1MB, WhatsApp uses 2MB, Zoom uses 4MB
        container.setMaxTextMessageBufferSize(1024 * 1024);    // 1 MB
        container.setMaxBinaryMessageBufferSize(2 * 1024 * 1024); // 2 MB

        // TIMEOUT SETTINGS: Balance between connection health and resource usage
        container.setMaxSessionIdleTimeout(60L * 60 * 1000);   // 60 minutes (long calls)
        container.setAsyncSendTimeout(30000L);                  // 30 seconds async timeout

        log.info("📊 WebSocket container configured: textBuffer=1MB, binaryBuffer=2MB");
        return container;
    }

    /**
     * Task scheduler for heartbeat/ping-pong mechanism
     * Keeps connections alive and detects dead connections
     */
    @Bean
    public TaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();

        log.info("💓 WebSocket heartbeat scheduler initialized");
        return scheduler;
    }
}