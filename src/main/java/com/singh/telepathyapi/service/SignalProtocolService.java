package com.singh.telepathyapi.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Signal Protocol Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignalProtocolService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String IDENTITY_KEY_PREFIX = "signal:identity:";
    private static final String PREKEY_BUNDLE_PREFIX = "signal:prekey:bundle:";
    private static final String SIGNED_PREKEY_PREFIX = "signal:prekey:signed:";
    private static final String ONETIME_PREKEY_PREFIX = "signal:prekey:onetime:";

    private static final int IDENTITY_KEY_TTL_DAYS = 365;
    private static final int SIGNED_PREKEY_TTL_DAYS = 30;
    private static final int ONETIME_PREKEY_TTL_DAYS = 30;

    public void storeIdentityKey(String userId, String publicIdentityKey) {
        String key = IDENTITY_KEY_PREFIX + userId;
        Map<String, Object> identityData = new HashMap<>();
        identityData.put("publicKey", publicIdentityKey);
        identityData.put("registrationId", generateRegistrationId());
        identityData.put("timestamp", System.currentTimeMillis());
        redisTemplate.opsForValue().set(key, identityData, IDENTITY_KEY_TTL_DAYS, TimeUnit.DAYS);
        log.info("Stored identity key for user: {}", userId);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getIdentityKey(String userId) {
        return (Map<String, Object>) redisTemplate.opsForValue().get(IDENTITY_KEY_PREFIX + userId);
    }

    public void storePreKeyBundle(String userId, PreKeyBundleDTO bundle) {
        String bundleKey = PREKEY_BUNDLE_PREFIX + userId;
        Map<String, Object> bundleData = new HashMap<>();
        bundleData.put("identityKey", bundle.getIdentityKey());
        bundleData.put("registrationId", bundle.getRegistrationId());
        bundleData.put("signedPreKey", bundle.getSignedPreKey());
        bundleData.put("signedPreKeySignature", bundle.getSignedPreKeySignature());
        bundleData.put("timestamp", System.currentTimeMillis());
        redisTemplate.opsForValue().set(bundleKey, bundleData, SIGNED_PREKEY_TTL_DAYS, TimeUnit.DAYS);

        if (bundle.getOneTimePreKeys() != null) {
            for (String preKey : bundle.getOneTimePreKeys()) {
                String key = ONETIME_PREKEY_PREFIX + userId + ":" + UUID.randomUUID();
                redisTemplate.opsForValue().set(key, preKey, ONETIME_PREKEY_TTL_DAYS, TimeUnit.DAYS);
            }
        }
        log.info("Stored pre-key bundle for user: {} with {} one-time keys",
                userId, bundle.getOneTimePreKeys() != null ? bundle.getOneTimePreKeys().size() : 0);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getPreKeyBundle(String userId) {
        String bundleKey = PREKEY_BUNDLE_PREFIX + userId;
        Map<String, Object> bundle = (Map<String, Object>) redisTemplate.opsForValue().get(bundleKey);
        if (bundle != null) {
            String oneTime = consumeOneTimePreKey(userId);
            if (oneTime != null) bundle.put("oneTimePreKey", oneTime);
        }
        return bundle;
    }

    private String consumeOneTimePreKey(String userId) {
        Set<String> keys = redisTemplate.keys(ONETIME_PREKEY_PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            String key = keys.iterator().next();
            String preKey = (String) redisTemplate.opsForValue().get(key);
            redisTemplate.delete(key);
            log.debug("Consumed one-time pre-key for user: {}", userId);
            return preKey;
        }
        log.warn("No one-time pre-keys available for user: {}", userId);
        return null;
    }

    public void rotateSignedPreKey(String userId, String newSignedPreKey, String signature) {
        String key = SIGNED_PREKEY_PREFIX + userId;
        Map<String, Object> data = new HashMap<>();
        data.put("preKey", newSignedPreKey);
        data.put("signature", signature);
        data.put("timestamp", System.currentTimeMillis());
        redisTemplate.opsForValue().set(key, data, SIGNED_PREKEY_TTL_DAYS, TimeUnit.DAYS);
        log.info("Rotated signed pre-key for user: {}", userId);
    }

    public void replenishOneTimePreKeys(String userId, List<String> newPreKeys) {
        for (String preKey : newPreKeys) {
            String key = ONETIME_PREKEY_PREFIX + userId + ":" + UUID.randomUUID();
            redisTemplate.opsForValue().set(key, preKey, ONETIME_PREKEY_TTL_DAYS, TimeUnit.DAYS);
        }
        log.info("Replenished {} one-time pre-keys for user: {}", newPreKeys.size(), userId);
    }

    public int getOneTimePreKeyCount(String userId) {
        Set<String> keys = redisTemplate.keys(ONETIME_PREKEY_PREFIX + userId + ":*");
        return keys != null ? keys.size() : 0;
    }

    public void deleteAllUserKeys(String userId) {
        Set<String> allKeys = redisTemplate.keys("signal:*:" + userId + "*");
        if (allKeys != null && !allKeys.isEmpty()) redisTemplate.delete(allKeys);
        log.info("Deleted all Signal keys for user: {}", userId);
    }

    private int generateRegistrationId() {
        return new SecureRandom().nextInt(16380) + 1;
    }

    @Data
    @Builder
    public static class PreKeyBundleDTO {
        private String identityKey;
        private int registrationId;
        private String signedPreKey;
        private String signedPreKeySignature;
        private List<String> oneTimePreKeys;
    }
}
