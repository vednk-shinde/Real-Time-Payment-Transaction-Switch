package com.paymentswitch.service;

import org.springframework.stereotype.Service;

@Service
public class CardMaskingService {

    /** {@code 4111111111111111 -> ****1111}. Only the last four digits ever leave this method. */
    public String mask(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        return "****" + cardNumber.substring(cardNumber.length() - 4);
    }

    /** The Bank Identification Number (first six digits) used for routing. */
    public String bin(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 6) {
            throw new IllegalArgumentException("Card number too short to contain a BIN");
        }
        return cardNumber.substring(0, 6);
    }
}
