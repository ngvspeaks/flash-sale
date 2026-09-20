package com.enterprise.flashsale.web;

import com.enterprise.flashsale.config.KafkaConfig;
import com.enterprise.flashsale.model.OrderEvent;
import com.enterprise.flashsale.service.AtomicInventoryService;
import com.enterprise.flashsale.service.AtomicInventoryService.ReservationResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final AtomicInventoryService inventoryService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;

    public OrderController(AtomicInventoryService inventoryService,
                           KafkaTemplate<String, Object> kafkaTemplate,
                           StringRedisTemplate redisTemplate) {
        this.inventoryService = inventoryService;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/orders/reserve")
    public ResponseEntity<Map<String, Object>> reserveStock(
            @RequestBody ReserveRequest request,
            HttpServletRequest servletRequest) {

        String ipAddress = servletRequest.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = servletRequest.getRemoteAddr();
        }
        String userAgent = servletRequest.getHeader("User-Agent");

        // 0. Quarantine Bot Check
        Boolean isQuarantined = redisTemplate.hasKey("quarantine:" + request.userId());
        if (Boolean.TRUE.equals(isQuarantined)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "errorCode", "BOT_QUARANTINED",
                "message", "User account flagged for automated velocity violation"
            ));
        }

        // 1. Edge & Atomic State Defense (Redis Lua Execution)
        ReservationResult result = inventoryService.reserveStock(
                request.skuId(),
                request.userId(),
                request.quantity() > 0 ? request.quantity() : 1,
                900, // 15 min TTL
                2    // Max 2 items per user
        );

        // 2. Sealed Interface Pattern Matching Response Handling
        return switch (result) {
            case ReservationResult.Success success -> {
                OrderEvent event = new OrderEvent(
                    UUID.randomUUID().toString(),
                    success.reservationId(),
                    success.skuId(),
                    success.userId(),
                    success.quantity(),
                    Instant.now().toEpochMilli(),
                    ipAddress,
                    userAgent,
                    0.05
                );
                
                // Asynchronous Event Publication to Kafka
                kafkaTemplate.send(KafkaConfig.TOPIC_ORDERS, success.skuId(), event);

                yield ResponseEntity.ok(Map.of(
                    "status", "RESERVED",
                    "reservationId", success.reservationId(),
                    "skuId", success.skuId(),
                    "quantity", success.quantity(),
                    "expiresInSeconds", 900
                ));
            }
            case ReservationResult.OutOfStock outOfStock ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "errorCode", "OUT_OF_STOCK",
                    "message", outOfStock.message()
                ));
            case ReservationResult.LimitExceeded limitExceeded ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "errorCode", "LIMIT_EXCEEDED",
                    "message", limitExceeded.message()
                ));
            case ReservationResult.Error error ->
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "errorCode", "SYSTEM_ERROR",
                    "message", error.message()
                ));
        };
    }

    @PostMapping("/admin/inventory/reset")
    public ResponseEntity<Map<String, Object>> resetInventory(@RequestBody ResetRequest request) {
        inventoryService.setStock(request.skuId(), request.stock());
        return ResponseEntity.ok(Map.of(
            "skuId", request.skuId(),
            "updatedStock", request.stock()
        ));
    }

    @GetMapping("/inventory/{skuId}")
    public ResponseEntity<Map<String, Object>> getInventory(@PathVariable String skuId) {
        Long currentStock = inventoryService.getStock(skuId);
        return ResponseEntity.ok(Map.of(
            "skuId", skuId,
            "availableStock", currentStock
        ));
    }

    @GetMapping("/agent/logs")
    public ResponseEntity<java.util.List<String>> getAgentLogs() {
        java.util.List<String> logs = redisTemplate.opsForList().range("agent:logs", 0, -1);
        return ResponseEntity.ok(logs != null ? logs : java.util.List.of());
    }

    public record ReserveRequest(String skuId, String userId, int quantity) {}
    public record ResetRequest(String skuId, int stock) {}
}
