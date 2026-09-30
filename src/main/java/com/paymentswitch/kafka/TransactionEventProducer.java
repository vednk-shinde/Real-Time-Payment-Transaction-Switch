package com.paymentswitch.kafka;

import com.paymentswitch.config.AppProperties;
import com.paymentswitch.model.TransactionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class TransactionEventProducer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventProducer.class);

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private final String topic;

    public TransactionEventProducer(KafkaTemplate<String, TransactionEvent> kafkaTemplate, AppProperties props) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = props.kafka().topic();
    }

    /**
     * Keyed by transaction id, so all events for one transaction go to the
     * same partition and keep their order.
     */
    public CompletableFuture<SendResult<String, TransactionEvent>> publish(TransactionEvent event) {
        return kafkaTemplate.send(topic, event.transactionId().toString(), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Failed to publish {} for transaction {}", event.status(), event.transactionId(), error);
                    } else {
                        log.debug("Published {} for transaction {} to {}-{}@{}", event.status(), event.transactionId(),
                                result.getRecordMetadata().topic(), result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
