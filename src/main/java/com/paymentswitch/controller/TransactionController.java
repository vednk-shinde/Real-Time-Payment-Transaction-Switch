package com.paymentswitch.controller;

import com.paymentswitch.dto.AuditRecordResponse;
import com.paymentswitch.dto.TransactionRequest;
import com.paymentswitch.dto.TransactionResponse;
import com.paymentswitch.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Submits a transaction to the switch. Returns 201 for every processed
     * transaction, approved or declined; the outcome is in {@code status}
     * and {@code responseCode}. 409 for a reused idempotency key.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> submit(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = transactionService.process(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id) {
        return transactionService.get(id);
    }

    /** The transaction's state transitions as recorded by the Kafka audit consumer. */
    @GetMapping("/{id}/events")
    public List<AuditRecordResponse> events(@PathVariable UUID id) {
        return transactionService.auditTrail(id);
    }

    /** Admin-only listing, newest first. */
    @GetMapping
    public Page<TransactionResponse> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionService.list(pageable);
    }
}
