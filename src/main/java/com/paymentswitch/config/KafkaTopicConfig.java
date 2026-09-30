package com.paymentswitch.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /**
     * Events are keyed by transaction id, so every state change of one
     * transaction lands on the same partition and is consumed in order,
     * while different transactions spread across partitions.
     */
    @Bean
    public NewTopic transactionEventsTopic(AppProperties props) {
        return TopicBuilder.name(props.kafka().topic())
                .partitions(props.kafka().partitions())
                .replicas(1)
                .build();
    }
}
