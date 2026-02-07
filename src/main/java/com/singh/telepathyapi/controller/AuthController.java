package com.singh.telepathyapi.controller;

import com.singh.telepathyapi.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Authentication Controller
 * 
 * Handles user registration and login
 * Minimal user data storage (only for authentication)
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String USER_PREFIX = "user:";
    private static final int USER_DATA_TTL_DAYS = 365;

    /**
     * Register new user
     * Only stores: userId, phone, hashed password, name
     * NO messages, NO conversations, NO metadata
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        String userId = UUID.randomUUID().toString();
        String userKey = USER_PREFIX + userId;

        // Check if phone already registered
        if (isPhoneRegistered(request.getPhone())) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Phone number already registered"
            ));
        }

        // Store minimal user data
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", userId);
        userData.put("phone", request.getPhone());
        userData.put("name", request.getName());
        userData.put("passwordHash", passwordEncoder.encode(request.getPassword()));
        userData.put("createdAt", System.currentTimeMillis());

        redisTemplate.opsForValue().set(
            userKey,
            userData,
            USER_DATA_TTL_DAYS,
            TimeUnit.DAYS
        );

        // Store phone -> userId mapping
        redisTemplate.opsForValue().set(
            "phone:" + request.getPhone(),
            userId,
            USER_DATA_TTL_DAYS,
            TimeUnit.DAYS
        );

        // Generate tokens
        String accessToken = tokenProvider.generateToken(userId);
        String refreshToken = tokenProvider.generateRefreshToken(userId);

        log.info("User registered: {}", userId);

        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "accessToken", accessToken,
            "refreshToken", refreshToken,
            "user", Map.of(
                "userId", userId,
                "name", request.getName(),
                "phone", request.getPhone()
            )
        ));
    }

    /**
     * Login existing user
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Get userId from phone
        String userId = (String) redisTemplate.opsForValue().get("phone:" + request.getPhone());

        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "User not found"
            ));
        }

        // Get user data
        String userKey = USER_PREFIX + userId;
        @SuppressWarnings("unchecked")
        Map<String, Object> userData = (Map<String, Object>) redisTemplate.opsForValue().get(userKey);

        if (userData == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "User not found"
            ));
        }

        // Verify password
        String storedPasswordHash = (String) userData.get("passwordHash");
        if (!passwordEncoder.matches(request.getPassword(), storedPasswordHash)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid credentials"
            ));
        }

        // Generate tokens
        String accessToken = tokenProvider.generateToken(userId);
        String refreshToken = tokenProvider.generateRefreshToken(userId);

        log.info("User logged in: {}", userId);

        return ResponseEntity.ok(Map.of(
            "userId", userId,
            "accessToken", accessToken,
            "refreshToken", refreshToken,
            "user", Map.of(
                "userId", userId,
                "name", userData.get("name"),
                "phone", userData.get("phone")
            )
        ));
    }

    /**
     * Refresh access token
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");

        if (!tokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid refresh token"
            ));
        }

        String userId = tokenProvider.getUserIdFromToken(refreshToken);
        String newAccessToken = tokenProvider.generateToken(userId);

        return ResponseEntity.ok(Map.of(
            "accessToken", newAccessToken
        ));
    }

    /**
     * Logout (client-side token deletion)
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        // Stateless - client just deletes token
        return ResponseEntity.ok(Map.of(
            "message", "Logged out successfully"
        ));
    }

    /**
     * Check if phone is already registered
     */
    private boolean isPhoneRegistered(String phone) {
        return redisTemplate.hasKey("phone:" + phone);
    }

    // DTOs
    @lombok.Data
    public static class RegisterRequest {
        private String phone;
        private String name;
        private String password;
    }

    @lombok.Data
    public static class LoginRequest {
        private String phone;
        private String password;
    }
}
