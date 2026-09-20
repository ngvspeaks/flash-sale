package com.enterprise.flashsale.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;

import java.util.concurrent.Executors;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_ORDERS = "flash-sale.orders";
    public static final String TOPIC_RELEASES = "flash-sale.releases";
    public static final String TOPIC_BOT_ALERTS = "flash-sale.bot-alerts";

    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name(TOPIC_ORDERS)
                .partitions(12) // High concurrency partition keying by SKU ID
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic stockReleasesTopic() {
        return TopicBuilder.name(TOPIC_RELEASES)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic botAlertsTopic() {
        return TopicBuilder.name(TOPIC_BOT_ALERTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // Virtual Thread Executor for Kafka Consumers to handle high concurrency with backpressure
        org.springframework.core.task.SimpleAsyncTaskExecutor executor = new org.springframework.core.task.SimpleAsyncTaskExecutor("kafka-vt-");
        executor.setVirtualThreads(true);
        factory.getContainerProperties().setListenerTaskExecutor(executor);
        factory.setConcurrency(12);
        return factory;
    }
}
