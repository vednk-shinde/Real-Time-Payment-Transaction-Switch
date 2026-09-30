package com.paymentswitch.service;

import com.paymentswitch.config.AppProperties;
import com.paymentswitch.dto.AuditRecordResponse;
import com.paymentswitch.dto.TransactionRequest;
import com.paymentswitch.dto.TransactionResponse;
import com.paymentswitch.exception.DuplicateTransactionException;
import com.paymentswitch.exception.TransactionNotFoundException;
import com.paymentswitch.model.Transaction;
import com.paymentswitch.model.TransactionEvent;
import com.paymentswitch.model.TransactionStatus;
import com.paymentswitch.repository.AuditRecordRepository;
import com.paymentswitch.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core switch pipeline: RECEIVED -> VALIDATED -> ROUTED -> APPROVED | DECLINED.
 *
 * Each transition raises a {@link TransactionEvent}. Those are NOT sent to
 * Kafka here: {@code kafka.TransactionEventRelay} forwards them only after
 * the database transaction commits, so Kafka never carries an event for a
 * transaction that was rolled back.
 */
@Service
public class TransactionService {

    static final String APPROVED = "00";
    static final String DO_NOT_HONOR = "05";
    static final String INVALID_CARD = "14";
    static final String NO_SUCH_ISSUER = "15";
    static final String EXCEEDS_LIMIT = "61";

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactions;
    private final AuditRecordRepository auditRecords;
    private final TransactionRoutingService routing;
    private final CardMaskingService masking;
    private final ApplicationEventPublisher events;
    private final BigDecimal maxAmount;

    public TransactionService(TransactionRepository transactions,
                              AuditRecordRepository auditRecords,
                              TransactionRoutingService routing,
                              CardMaskingService masking,
                              ApplicationEventPublisher events,
                              AppProperties props) {
        this.transactions = transactions;
        this.auditRecords = auditRecords;
        this.routing = routing;
        this.masking = masking;
        this.events = events;
        this.maxAmount = props.risk().maxAmount();
    }

    @Transactional
    public TransactionResponse process(TransactionRequest request) {
        // Fast path for client retries. The unique constraint below is what
        // actually guarantees idempotency when two retries race each other.
        if (transactions.existsByIdempotencyKey(request.idempotencyKey())) {
            throw new DuplicateTransactionException(request.idempotencyKey());
        }

        String pan = request.cardNumber();
        Transaction tx = new Transaction(
                request.idempotencyKey(),
                masking.mask(pan),
                masking.bin(pan),
                request.merchantId(),
                request.acquirerId(),
                request.amount(),
                request.currency(),
                request.channel());
        try {
            tx = transactions.saveAndFlush(tx);
        } catch (DataIntegrityViolationException raceLost) {
            throw new DuplicateTransactionException(request.idempotencyKey());
        }
        emit(tx, null);

        // 1. Validate
        if (!LuhnValidator.isValid(pan)) {
            return finish(decline(tx, INVALID_CARD, "Invalid card number (Luhn check failed)"));
        }
        tx.markValidated();
        emit(tx, TransactionStatus.RECEIVED);

        // 2. Route
        Optional<String> issuer = routing.resolveIssuer(tx.getCardBin());
        if (issuer.isEmpty()) {
            return finish(decline(tx, NO_SUCH_ISSUER, "No route to an issuer for BIN " + tx.getCardBin()));
        }
        tx.markRouted(issuer.get());
        emit(tx, TransactionStatus.VALIDATED);

        // 3. Authorize (simulated issuer + demo risk rule)
        if (tx.getAmount().compareTo(maxAmount) > 0) {
            decline(tx, EXCEEDS_LIMIT, "Amount exceeds limit of " + maxAmount);
        } else {
            tx.approve(APPROVED);
            emit(tx, TransactionStatus.ROUTED);
        }
        return finish(tx);
    }

    private Transaction decline(Transaction tx, String responseCode, String reason) {
        TransactionStatus previous = tx.getStatus();
        tx.decline(responseCode, reason);
        emit(tx, previous);
        return tx;
    }

    private TransactionResponse finish(Transaction tx) {
        Transaction saved = transactions.saveAndFlush(tx);
        log.info("Transaction {} merchant={} amount={} {} -> {} ({})", saved.getId(), saved.getMerchantId(),
                saved.getAmount(), saved.getCurrency(), saved.getStatus(), saved.getResponseCode());
        return TransactionResponse.from(saved);
    }

    private void emit(Transaction tx, TransactionStatus previous) {
        events.publishEvent(TransactionEvent.of(tx, previous));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID id) {
        return transactions.findById(id)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> list(Pageable pageable) {
        return transactions.findAll(pageable).map(TransactionResponse::from);
    }

    /** The audit trail as projected from Kafka by {@code TransactionEventConsumer}. */
    @Transactional(readOnly = true)
    public List<AuditRecordResponse> auditTrail(UUID id) {
        if (!transactions.existsById(id)) {
            throw new TransactionNotFoundException(id);
        }
        return auditRecords.findByTransactionIdOrderByOccurredAtAscIdAsc(id).stream()
                .map(AuditRecordResponse::from)
                .toList();
    }
}
