package com.paymentswitch.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Resolves the issuer for a card from its BIN. A real switch looks this up
 * in an issuer/BIN directory; here the table is a fixed set of scheme
 * prefixes so routing is deterministic and testable. The shape is the same:
 * routing only decides *where* a transaction goes, never *whether* it is
 * approved.
 */
@Service
public class TransactionRoutingService {

    private record BinRange(long low, long high, int prefixLength, String issuerId) {
        boolean matches(String bin) {
            long prefix = Long.parseLong(bin.substring(0, prefixLength));
            return prefix >= low && prefix <= high;
        }
    }

    // Longer / more specific prefixes first.
    private static final List<BinRange> ROUTING_TABLE = List.of(
            new BinRange(2221, 2720, 4, "ISSUER-MASTERCARD-SIM"),
            new BinRange(6011, 6011, 4, "ISSUER-DISCOVER-SIM"),
            new BinRange(34, 34, 2, "ISSUER-AMEX-SIM"),
            new BinRange(37, 37, 2, "ISSUER-AMEX-SIM"),
            new BinRange(51, 55, 2, "ISSUER-MASTERCARD-SIM"),
            new BinRange(65, 65, 2, "ISSUER-DISCOVER-SIM"),
            new BinRange(4, 4, 1, "ISSUER-VISA-SIM"));

    public Optional<String> resolveIssuer(String bin) {
        if (bin == null || bin.length() < 4 || !bin.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        return ROUTING_TABLE.stream()
                .filter(range -> range.matches(bin))
                .map(BinRange::issuerId)
                .findFirst();
    }
}
