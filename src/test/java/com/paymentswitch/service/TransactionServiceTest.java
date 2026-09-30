package com.paymentswitch.service;

import com.paymentswitch.config.AppProperties;
import com.paymentswitch.dto.TransactionRequest;
import com.paymentswitch.dto.TransactionResponse;
import com.paymentswitch.exception.DuplicateTransactionException;
import com.paymentswitch.exception.TransactionNotFoundException;
import com.paymentswitch.model.Channel;
import com.paymentswitch.model.Transaction;
import com.paymentswitch.model.TransactionEvent;
import com.paymentswitch.model.TransactionStatus;
import com.paymentswitch.repository.AuditRecordRepository;
import com.paymentswitch.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock TransactionRepository transactions;
    @Mock AuditRecordRepository auditRecords;
    @Mock ApplicationEventPublisher events;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(null, null, null,
                new AppProperties.Risk(new BigDecimal("10000.00")), true);
        service = new TransactionService(transactions, auditRecords, new TransactionRoutingService(),
                new CardMaskingService(), events, props);
    }

    private void persistAssignsId() {
        lenient().when(transactions.saveAndFlush(any(Transaction.class))).thenAnswer(inv -> {
            Transaction tx = inv.getArgument(0);
            if (tx.getId() == null) ReflectionTestUtils.setField(tx, "id", UUID.randomUUID());
            return tx;
        });
    }

    private static TransactionRequest request(String key, String pan, String amount) {
        return new TransactionRequest(key, pan, "MERCHANT-42", "ACQUIRER-7", new BigDecimal(amount), "USD", Channel.ECOMMERCE);
    }

    private List<TransactionEvent> publishedEvents(int expected) {
        ArgumentCaptor<TransactionEvent> captor = ArgumentCaptor.forClass(TransactionEvent.class);
        verify(events, times(expected)).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void approvesValidRoutableTransactionAndEmitsEveryTransition() {
        persistAssignsId();

        TransactionResponse response = service.process(request("k1", "4111111111111111", "150.00"));

        assertThat(response.status()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(response.responseCode()).isEqualTo("00");
        assertThat(response.issuerId()).isEqualTo("ISSUER-VISA-SIM");
        assertThat(response.maskedCardNumber()).isEqualTo("****1111");

        List<TransactionEvent> emitted = publishedEvents(4);
        assertThat(emitted).extracting(TransactionEvent::status).containsExactly(
                TransactionStatus.RECEIVED, TransactionStatus.VALIDATED, TransactionStatus.ROUTED, TransactionStatus.APPROVED);
        assertThat(emitted).extracting(TransactionEvent::previousStatus).containsExactly(
                null, TransactionStatus.RECEIVED, TransactionStatus.VALIDATED, TransactionStatus.ROUTED);
        assertThat(emitted).allSatisfy(e -> assertThat(e.maskedCardNumber()).isEqualTo("****1111"));
    }

    @Test
    void declinesAmountsOverTheRiskLimit() {
        persistAssignsId();

        TransactionResponse response = service.process(request("k2", "5555555555554444", "10000.01"));

        assertThat(response.status()).isEqualTo(TransactionStatus.DECLINED);
        assertThat(response.responseCode()).isEqualTo("61");
        assertThat(publishedEvents(4)).last().extracting(TransactionEvent::previousStatus).isEqualTo(TransactionStatus.ROUTED);
    }

    @Test
    void amountExactlyAtLimitIsApproved() {
        persistAssignsId();
        assertThat(service.process(request("k3", "4111111111111111", "10000.00")).status()).isEqualTo(TransactionStatus.APPROVED);
    }

    @Test
    void declinesCardsFailingTheLuhnCheckBeforeRouting() {
        persistAssignsId();

        TransactionResponse response = service.process(request("k4", "4111111111111112", "10.00"));

        assertThat(response.status()).isEqualTo(TransactionStatus.DECLINED);
        assertThat(response.responseCode()).isEqualTo("14");
        assertThat(response.issuerId()).isNull();
        assertThat(publishedEvents(2)).extracting(TransactionEvent::status)
                .containsExactly(TransactionStatus.RECEIVED, TransactionStatus.DECLINED);
    }

    @Test
    void declinesUnroutableBins() {
        persistAssignsId();

        // Luhn-valid, but 9xxxxx has no issuer in the routing table.
        TransactionResponse response = service.process(request("k5", "9000000000000001", "10.00"));

        assertThat(response.status()).isEqualTo(TransactionStatus.DECLINED);
        assertThat(response.responseCode()).isEqualTo("15");
    }

    @Test
    void rejectsReusedIdempotencyKeyWithoutProcessing() {
        when(transactions.existsByIdempotencyKey("dup")).thenReturn(true);

        assertThatThrownBy(() -> service.process(request("dup", "4111111111111111", "1.00")))
                .isInstanceOf(DuplicateTransactionException.class);
        verify(transactions, never()).saveAndFlush(any());
        verifyNoInteractions(events);
    }

    @Test
    void losingAnIdempotencyRaceAtTheDatabaseIsAlsoADuplicate() {
        when(transactions.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> service.process(request("race", "4111111111111111", "1.00")))
                .isInstanceOf(DuplicateTransactionException.class);
        verifyNoInteractions(events);
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        when(transactions.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void auditTrailRequiresAnExistingTransaction() {
        UUID id = UUID.randomUUID();
        when(transactions.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.auditTrail(id)).isInstanceOf(TransactionNotFoundException.class);
    }
}
