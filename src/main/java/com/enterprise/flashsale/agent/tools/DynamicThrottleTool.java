package com.enterprise.flashsale.agent.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.function.Function;

@Configuration
public class DynamicThrottleTool {

    private static final Logger log = LoggerFactory.getLogger(DynamicThrottleTool.class);
    private final StringRedisTemplate redisTemplate;

    public DynamicThrottleTool(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public record ThrottleRequest(String ipAddress, int maxRequestsPerMinute) {}

    @Bean
    @Description("Dynamically adjusts ingress rate-limiting parameters in Redis for specific IP ranges or subnet masks")
    public Function<ThrottleRequest, String> adjustRateLimitFunction() {
        return request -> {
            log.info("[SENTINEL THROTTLE] Dynamically setting IP limit for {}: {} req/min", request.ipAddress(), request.maxRequestsPerMinute());

            String key = "ratelimit:config:" + request.ipAddress();
            redisTemplate.opsForValue().set(key, String.valueOf(request.maxRequestsPerMinute()), Duration.ofHours(1));

            return "SUCCESS: Dynamic rate limit set to " + request.maxRequestsPerMinute() + " req/min for IP: " + request.ipAddress();
        };
    }

    public String adjustRateLimit(String ipAddress, int maxRequestsPerMinute) {
        return adjustRateLimitFunction().apply(new ThrottleRequest(ipAddress, maxRequestsPerMinute));
    }
}

