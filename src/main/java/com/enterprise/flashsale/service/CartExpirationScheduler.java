package com.enterprise.flashsale.service;

import com.enterprise.flashsale.config.KafkaConfig;
import com.enterprise.flashsale.model.StockReleaseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class CartExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CartExpirationScheduler.class);

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final DefaultRedisScript<Long> releaseScript;

    public CartExpirationScheduler(StringRedisTemplate redisTemplate,
                                   KafkaTemplate<String, Object> kafkaTemplate) {
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.releaseScript = new DefaultRedisScript<>();
        this.releaseScript.setScriptSource(
            new ResourceScriptSource(new ClassPathResource("scripts/release_stock.lua"))
        );
        this.releaseScript.setResultType(Long.class);
    }

    @Scheduled(fixedDelay = 500)
    public void processExpiredCarts() {
        // High-frequency Virtual-Threaded poll of active SKU reservation ZSETs
        List<String> activeSkus = List.of("1001", "1002", "1003");
        long nowMs = Instant.now().toEpochMilli();

        for (String skuId : activeSkus) {
            String reservedKey = "{sku:" + skuId + "}:reserved";
            String stockKey = "{sku:" + skuId + "}:stock";

            Long releasedCount = redisTemplate.execute(
                releaseScript,
                List.of(reservedKey, stockKey),
                String.valueOf(nowMs),
                "100" // Batch size
            );

            if (releasedCount != null && releasedCount > 0) {
                log.info("[SCHEDULER] Dynamically released {} expired cart items back to stock for SKU: {}", releasedCount, skuId);
                
                // Broadcast event for dynamic analytics & inventory monitoring
                StockReleaseEvent event = new StockReleaseEvent(
                    skuId,
                    releasedCount.intValue(),
                    nowMs,
                    "CART_RESERVATION_TIMEOUT"
                );
                kafkaTemplate.send(KafkaConfig.TOPIC_RELEASES, skuId, event);
            }
        }
    }
}
