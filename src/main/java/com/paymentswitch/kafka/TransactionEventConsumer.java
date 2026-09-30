package com.paymentswitch.kafka;

import com.paymentswitch.model.AuditRecord;
import com.paymentswitch.model.TransactionEvent;
import com.paymentswitch.repository.AuditRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Audit projection: folds the event stream into the {@code transaction_audit}
 * table, independent of the synchronous request path. Kafka delivers
 * at-least-once, so the same event can arrive twice; the unique eventId
 * makes that a no-op instead of a duplicate audit row.
 */
@Component
public class TransactionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventConsumer.class);

    private final AuditRecordRepository auditRecords;

    public TransactionEventConsumer(AuditRecordRepository auditRecords) {
        this.auditRecords = auditRecords;
    }

    @KafkaListener(topics = "${app.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onEvent(@Payload TransactionEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {
        if (auditRecords.existsByEventId(event.eventId())) {
            log.debug("Skipping redelivered event {}", event.eventId());
            return;
        }
        try {
            auditRecords.save(new AuditRecord(event, partition, offset));
        } catch (DataIntegrityViolationException concurrentDuplicate) {
            log.debug("Event {} was recorded concurrently; skipping", event.eventId());
        }
    }
}
