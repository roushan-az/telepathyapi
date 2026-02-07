package com.singh.telepathyapi.controller;

import com.singh.telepathyapi.service.SignalProtocolService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Signal Protocol Controller
 * 
 * Handles Signal Protocol key exchange
 * Enables end-to-end encryption
 */
@RestController
@RequestMapping("/api/signal")
@RequiredArgsConstructor
public class SignalProtocolController {

    private final SignalProtocolService signalService;

    /**
     * Upload identity key (public key only)
     */
    @PostMapping("/keys/identity")
    public ResponseEntity<?> uploadIdentityKey(
            @AuthenticationPrincipal String userId,
            @RequestBody Map<String, String> request) {
        
        String publicKey = request.get("publicKey");
        signalService.storeIdentityKey(userId, publicKey);
        
        return ResponseEntity.ok(Map.of(
            "message", "Identity key uploaded successfully"
        ));
    }

    /**
     * Get user's identity key
     */
    @GetMapping("/keys/identity/{userId}")
    public ResponseEntity<?> getIdentityKey(@PathVariable String userId) {
        Map<String, Object> identityKey = signalService.getIdentityKey(userId);
        
        if (identityKey == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(identityKey);
    }

    /**
     * Upload pre-key bundle
     */
    @PostMapping("/keys/prekey-bundle")
    public ResponseEntity<?> uploadPreKeyBundle(
            @AuthenticationPrincipal String userId,
            @RequestBody PreKeyBundleRequest request) {
        
        SignalProtocolService.PreKeyBundleDTO bundle = SignalProtocolService.PreKeyBundleDTO.builder()
            .identityKey(request.getIdentityKey())
            .registrationId(request.getRegistrationId())
            .signedPreKey(request.getSignedPreKey())
            .signedPreKeySignature(request.getSignedPreKeySignature())
            .oneTimePreKeys(request.getOneTimePreKeys())
            .build();
        
        signalService.storePreKeyBundle(userId, bundle);
        
        return ResponseEntity.ok(Map.of(
            "message", "Pre-key bundle uploaded successfully",
            "oneTimeKeysCount", request.getOneTimePreKeys() != null ? request.getOneTimePreKeys().size() : 0
        ));
    }

    /**
     * Get pre-key bundle for starting encrypted session
     */
    @GetMapping("/keys/prekey-bundle/{userId}")
    public ResponseEntity<?> getPreKeyBundle(@PathVariable String userId) {
        Map<String, Object> bundle = signalService.getPreKeyBundle(userId);
        
        if (bundle == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(bundle);
    }

    /**
     * Rotate signed pre-key
     */
    @PostMapping("/keys/rotate-signed-prekey")
    public ResponseEntity<?> rotateSignedPreKey(
            @AuthenticationPrincipal String userId,
            @RequestBody Map<String, String> request) {
        
        String newPreKey = request.get("signedPreKey");
        String signature = request.get("signature");
        
        signalService.rotateSignedPreKey(userId, newPreKey, signature);
        
        return ResponseEntity.ok(Map.of(
            "message", "Signed pre-key rotated successfully"
        ));
    }

    /**
     * Replenish one-time pre-keys
     */
    @PostMapping("/keys/replenish-prekeys")
    public ResponseEntity<?> replenishPreKeys(
            @AuthenticationPrincipal String userId,
            @RequestBody Map<String, List<String>> request) {
        
        List<String> newPreKeys = request.get("preKeys");
        signalService.replenishOneTimePreKeys(userId, newPreKeys);
        
        return ResponseEntity.ok(Map.of(
            "message", "One-time pre-keys replenished",
            "count", newPreKeys.size()
        ));
    }

    /**
     * Get one-time pre-key count
     */
    @GetMapping("/keys/prekey-count")
    public ResponseEntity<?> getPreKeyCount(@AuthenticationPrincipal String userId) {
        int count = signalService.getOneTimePreKeyCount(userId);
        
        return ResponseEntity.ok(Map.of(
            "count", count
        ));
    }

    // DTOs
    @lombok.Data
    public static class PreKeyBundleRequest {
        private String identityKey;
        private int registrationId;
        private String signedPreKey;
        private String signedPreKeySignature;
        private List<String> oneTimePreKeys;
    }
}
