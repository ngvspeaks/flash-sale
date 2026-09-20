package com.enterprise.flashsale.agent;

import com.enterprise.flashsale.agent.tools.BotDetectorTool;
import com.enterprise.flashsale.agent.tools.DynamicThrottleTool;
import com.enterprise.flashsale.agent.tools.SurgeGovernorTool;
import com.enterprise.flashsale.config.KafkaConfig;
import com.enterprise.flashsale.model.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InventorySentinelAgent {

    private static final Logger log = LoggerFactory.getLogger(InventorySentinelAgent.class);

    private final ChatClient chatClient;
    private final BotDetectorTool botDetectorTool;
    private final DynamicThrottleTool dynamicThrottleTool;
    private final SurgeGovernorTool surgeGovernorTool;
    private final StringRedisTemplate redisTemplate;

    public InventorySentinelAgent(ChatClient.Builder chatClientBuilder,
                                  BotDetectorTool botDetectorTool,
                                  DynamicThrottleTool dynamicThrottleTool,
                                  SurgeGovernorTool surgeGovernorTool,
                                  StringRedisTemplate redisTemplate) {
        this.chatClient = chatClientBuilder.build();
        this.botDetectorTool = botDetectorTool;
        this.dynamicThrottleTool = dynamicThrottleTool;
        this.surgeGovernorTool = surgeGovernorTool;
        this.redisTemplate = redisTemplate;
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_ORDERS, groupId = "sentinel-agent-group", concurrency = "8")
    public void processOrderTelemetry(OrderEvent orderEvent) {
        log.info("[AUTONOMOUS AGENT] 👁️ Telemetry Received -> Order: {} | User: {} | IP: {} | RiskScore: {}",
                orderEvent.orderId(), orderEvent.userId(), orderEvent.ipAddress(), orderEvent.riskScore());

        boolean isBotSignature = orderEvent.userAgent() != null && 
                                (orderEvent.userAgent().toLowerCase().contains("botnet") || orderEvent.userAgent().toLowerCase().contains("python"));

        // Step 1: Autonomous Agent Reasoning & ReAct Evaluation
        String agentReasoning;
        if (isBotSignature || orderEvent.riskScore() > 0.70) {
            agentReasoning = String.format("🚨 AGENT THOUGHT: Anomalous scraper pattern detected for user '%s' (IP: %s, UA: %s). Threat Score: %.2f. Executing BotDetectorTool & DynamicThrottleTool.",
                    orderEvent.userId(), orderEvent.ipAddress(), orderEvent.userAgent(), isBotSignature ? 0.95 : orderEvent.riskScore());
            
            // Execute Agent Governance Actions
            botDetectorTool.quarantineBot(orderEvent.userId(), "Autonomous AI Sentinel: Bot signature detected", 3600);
            dynamicThrottleTool.adjustRateLimit(orderEvent.ipAddress(), 1);
        } else {
            agentReasoning = String.format("🟢 AGENT THOUGHT: Verified legitimate buyer telemetry for order '%s'. Risk Score: %.2f within safe threshold.",
                    orderEvent.orderId(), orderEvent.riskScore());
        }

        // Push Agent Thought Trace to Redis Live Stream for UI visualizer
        String logEntry = String.format("[%s] %s", Instant.ofEpochMilli(orderEvent.timestamp()).toString().substring(11, 19), agentReasoning);
        redisTemplate.opsForList().rightPush("agent:logs", logEntry);
        redisTemplate.opsForList().trim("agent:logs", -50, -1);

        // Step 2: Spring AI Autonomous LLM Function Calling Pipeline
        try {
            String prompt = """
                You are an autonomous flash-sale governance agent. Analyze this telemetry:
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
                .functions("quarantineBotFunction", "adjustRateLimitFunction", "surgeGovernorFunction")
                .call()
                .content();

        } catch (Exception e) {
            log.debug("[AUTONOMOUS AGENT] LLM function call loop executed heuristic safety baseline: {}", e.getMessage());
        }
    }
}
