package com.enterprise.flashsale.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.function.Function;

@Configuration
public class SurgeGovernorTool {

    private static final Logger log = LoggerFactory.getLogger(SurgeGovernorTool.class);
    private final StringRedisTemplate redisTemplate;

    public SurgeGovernorTool(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public record SurgeGovernorRequest(String skuId, int updatedCartTtlSeconds, int maxPerUserLimit) {}

    @Bean
    @Description("Dynamically adjusts cart reservation TTL and max per-user limits under extreme surge traffic velocity")
    public Function<SurgeGovernorRequest, String> surgeGovernorFunction() {
        return request -> {
            log.warn("[AGENT SURGE GOVERNOR] Dynamically tuning inventory policy for SKU: {} | Cart TTL: {}s | Max Per User: {}",
                    request.skuId(), request.updatedCartTtlSeconds(), request.maxPerUserLimit());

            redisTemplate.opsForValue().set("config:sku:" + request.skuId() + ":ttl", String.valueOf(request.updatedCartTtlSeconds()));
            redisTemplate.opsForValue().set("config:sku:" + request.skuId() + ":max_limit", String.valueOf(request.maxPerUserLimit()));

            return "SUCCESS: Updated SKU " + request.skuId() + " policy -> TTL: " + request.updatedCartTtlSeconds() + "s, MaxLimit: " + request.maxPerUserLimit();
        };
    }
}
