package com.enterprise.flashsale.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AtomicInventoryService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> reserveScript;

    public AtomicInventoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.reserveScript = new DefaultRedisScript<>();
        this.reserveScript.setScriptSource(
            new ResourceScriptSource(new ClassPathResource("scripts/reserve_stock.lua"))
        );
        this.reserveScript.setResultType(Long.class);
    }

    public ReservationResult reserveStock(String skuId, String userId, int quantity, int cartTtlSeconds, int maxPerUser) {
        // Enforce Redis Hash Slot Tagging {sku:ID} to guarantee keys reside on the same cluster shard
        String stockKey = "{sku:" + skuId + "}:stock";
        String reservedKey = "{sku:" + skuId + "}:reserved";
        String userLimitKey = "{sku:" + skuId + "}:user:" + userId + ":count";
        String reservationId = UUID.randomUUID().toString();
        long nowMs = Instant.now().toEpochMilli();

        List<String> keys = List.of(stockKey, reservedKey, userLimitKey);
        Long result = redisTemplate.execute(
            reserveScript,
            keys,
            String.valueOf(quantity),
            String.valueOf(maxPerUser),
            String.valueOf(cartTtlSeconds),
            String.valueOf(nowMs),
            reservationId
        );

        if (result == null) {
            return new ReservationResult.Error("Redis script returned null execution result");
        }

        return switch (result.intValue()) {
            case 1 -> new ReservationResult.Success(reservationId, skuId, userId, quantity);
            case -1 -> new ReservationResult.OutOfStock("Stock depleted for SKU: " + skuId);
            case -2 -> new ReservationResult.LimitExceeded("User limit reached for SKU: " + skuId);
            default -> new ReservationResult.Error("Unknown Redis script status code: " + result);
        };
    }

    public void setStock(String skuId, int stockCount) {
        String stockKey = "{sku:" + skuId + "}:stock";
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stockCount));
    }

    public Long getStock(String skuId) {
        String stockKey = "{sku:" + skuId + "}:stock";
        String val = redisTemplate.opsForValue().get(stockKey);
        return val != null ? Long.parseLong(val) : 0L;
    }

    public sealed interface ReservationResult {
        record Success(String reservationId, String skuId, String userId, int quantity) implements ReservationResult {}
        record OutOfStock(String message) implements ReservationResult {}
        record LimitExceeded(String message) implements ReservationResult {}
        record Error(String message) implements ReservationResult {}
    }
}
