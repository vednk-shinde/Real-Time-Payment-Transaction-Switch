package com.paymentswitch.kafka;

import com.paymentswitch.model.TransactionEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges in-process transition events to Kafka, but only once the
 * database transaction that produced them has committed. If the request
 * rolls back (e.g. a duplicate idempotency key lost a race), none of its
 * events are ever published.
 */
@Component
public class TransactionEventRelay {

    private final TransactionEventProducer producer;

    public TransactionEventRelay(TransactionEventProducer producer) {
        this.producer = producer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(TransactionEvent event) {
        producer.publish(event);
    }
}
