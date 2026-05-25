package com.algoverse.collaboration.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks which users are currently connected to a room using Redis sets.
 * TTL is refreshed on each heartbeat; expires when session drops.
 */
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final Duration PRESENCE_TTL = Duration.ofMinutes(2);
    private static final String KEY_PREFIX = "collab:presence:";

    private final RedisTemplate<String, Object> redis;

    public void markOnline(UUID roomId, UUID userId) {
        String key = KEY_PREFIX + roomId;
        redis.opsForSet().add(key, userId.toString());
        redis.expire(key, PRESENCE_TTL);
    }

    public void markOffline(UUID roomId, UUID userId) {
        redis.opsForSet().remove(KEY_PREFIX + roomId, userId.toString());
    }

    public boolean isOnline(UUID roomId, UUID userId) {
        return Boolean.TRUE.equals(
                redis.opsForSet().isMember(KEY_PREFIX + roomId, userId.toString())
        );
    }

    @SuppressWarnings("unchecked")
    public Set<Object> getOnlineUsers(UUID roomId) {
        Set<Object> members = redis.opsForSet().members(KEY_PREFIX + roomId);
        return members != null ? members : Set.of();
    }
}
