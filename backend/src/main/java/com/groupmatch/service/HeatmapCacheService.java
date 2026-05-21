package com.groupmatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groupmatch.dto.availability.HeatmapResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeatmapCacheService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${app.cache.heatmap-ttl-minutes:60}")
    private int ttlMinutes;

    public Optional<HeatmapResponse> get(UUID groupId, int version, Instant from, Instant to, int granularity) {
        String key = cacheKey(groupId, version, from, to, granularity);
        log.debug("Cache GET: key={}", key);
        try {
            String json = redis.opsForValue().get(key);
            log.debug("Cache {}: key={}", json != null ? "HIT" : "MISS", key);
            if (json != null) {
                return Optional.of(objectMapper.readValue(json, HeatmapResponse.class));
            }
        } catch (Exception e) {
            log.error("Heatmap cache read failed for key={}", key, e);
        }
        return Optional.empty();
    }

    public void put(UUID groupId, int version, Instant from, Instant to, int granularity, HeatmapResponse response) {
        String key = cacheKey(groupId, version, from, to, granularity);
        try {
            String json = objectMapper.writeValueAsString(response);
            redis.opsForValue().set(key, json, Duration.ofMinutes(ttlMinutes));
            log.debug("Cache PUT: key={}, ttl={}min", key, ttlMinutes);
        } catch (Exception e) {
            log.error("Heatmap cache write failed for key={}", key, e);
        }
    }

    private String cacheKey(UUID groupId, int version, Instant from, Instant to, int granularity) {
        return String.format("heatmap:%s:%d:%d:%d:%d",
                groupId, version, from.getEpochSecond(), to.getEpochSecond(), granularity);
    }
}
