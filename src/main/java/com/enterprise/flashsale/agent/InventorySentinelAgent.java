package com.enterprise.flashsale.agent;

import com.enterprise.flashsale.agent.tools.BotDetectorTool;
import com.enterprise.flashsale.agent.tools.DynamicThrottleTool;
import com.enterprise.flashsale.config.KafkaConfig;
import com.enterprise.flashsale.model.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventorySentinelAgent {

    private static final Logger log = LoggerFactory.getLogger(InventorySentinelAgent.class);

    private final ChatClient chatClient;
    private final BotDetectorTool botDetectorTool;
    private final DynamicThrottleTool dynamicThrottleTool;

    public InventorySentinelAgent(ChatClient.Builder chatClientBuilder,
                                  BotDetectorTool botDetectorTool,
                                  DynamicThrottleTool dynamicThrottleTool) {
        this.chatClient = chatClientBuilder.build();
        this.botDetectorTool = botDetectorTool;
        this.dynamicThrottleTool = dynamicThrottleTool;
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_ORDERS, groupId = "sentinel-agent-group", concurrency = "8")
    public void processOrderTelemetry(OrderEvent orderEvent) {
        log.debug("[SENTINEL AGENT] Evaluating telemetry for Order: {} | User: {} | IP: {}",
                orderEvent.orderId(), orderEvent.userId(), orderEvent.ipAddress());

        // Fast path check for high risk
        if (orderEvent.userAgent() != null && orderEvent.userAgent().toLowerCase().contains("botnet")) {
            botDetectorTool.quarantineBot(orderEvent.userId(), "Known botnet User-Agent pattern", 3600);
            dynamicThrottleTool.adjustRateLimit(orderEvent.ipAddress(), 1);
            return;
        }

        // Autonomous LLM evaluation via Spring AI tool calling for ambiguous anomalies
        try {
            String prompt = """
                Analyze this flash-sale order event telemetry:
                - Order ID: %s
                - User ID: %s
                - IP Address: %s
                - User Agent: %s
                - Quantity: %d
                - Risk Score: %.2f

                If suspicious bot automation or rapid velocity scraping is detected, call BotDetectorTool to quarantine the user or DynamicThrottleTool to restrict the IP.
                """.formatted(
                    orderEvent.orderId(),
                    orderEvent.userId(),
                    orderEvent.ipAddress(),
                    orderEvent.userAgent(),
                    orderEvent.quantity(),
                    orderEvent.riskScore()
            );

            chatClient.prompt()
                .user(prompt)
                .functions("quarantineBotFunction", "adjustRateLimitFunction")
                .call()
                .content();

        } catch (Exception e) {
            log.error("[SENTINEL AGENT] Autonomous evaluation error, falling back to heuristic defense", e);
        }
    }
}
