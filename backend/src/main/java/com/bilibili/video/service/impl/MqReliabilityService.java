package com.bilibili.video.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqReliabilityService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${mq.reliability.consumer.dedup-prefix:mq:consume:done}")
    private String consumeDedupPrefix;

    @Value("${mq.reliability.consumer.dedup-ttl-hours:168}")
    private long consumeDedupTtlHours;

    public void consumeWithIdempotency(String topic, Object payload, Runnable handler) {
        String payloadHash = digestPayload(payload);
        String dedupKey = consumeDedupPrefix + ":" + topic + ":" + payloadHash;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(dedupKey))) {
            log.info("[MQ] duplicate message skipped, topic={}, hash={}", topic, payloadHash);
            return;
        }

        handler.run();

        redisTemplate.opsForValue().set(dedupKey, "1", Duration.ofHours(consumeDedupTtlHours));
        log.debug("[MQ] consume success marked idempotent, topic={}, hash={}", topic, payloadHash);
    }

    private String digestPayload(Object payload) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            String fallback = String.valueOf(payload);
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(fallback.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception ignored) {
                return Integer.toHexString(fallback.hashCode());
            }
        }
    }
}
