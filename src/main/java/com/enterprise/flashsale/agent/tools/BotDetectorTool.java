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
public class BotDetectorTool {

    private static final Logger log = LoggerFactory.getLogger(BotDetectorTool.class);
    private final StringRedisTemplate redisTemplate;

    public BotDetectorTool(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public record QuarantineRequest(String identifier, String reason, int quarantineDurationSeconds) {}

    @Bean
    @Description("Quarantines a suspected bot user ID or IP address by creating an atomic Redis block key with expiration")
    public Function<QuarantineRequest, String> quarantineBotFunction() {
        return request -> {
            log.warn("[BOT SENTINEL] Quarantining suspected bot identifier: {} | Reason: {} | Duration: {}s",
                    request.identifier(), request.reason(), request.quarantineDurationSeconds());

            String key = "quarantine:" + request.identifier();
            redisTemplate.opsForValue().set(key, request.reason(), Duration.ofSeconds(request.quarantineDurationSeconds()));

            return "SUCCESS: Quarantined " + request.identifier() + " for " + request.quarantineDurationSeconds() + " seconds. Reason: " + request.reason();
        };
    }

    public String quarantineBot(String identifier, String reason, int quarantineDurationSeconds) {
        return quarantineBotFunction().apply(new QuarantineRequest(identifier, reason, quarantineDurationSeconds));
    }
}

