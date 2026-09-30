package com.paymentswitch.service;

/**
 * Luhn (mod 10) checksum used by every major card scheme to catch typos in
 * a card number before it is routed anywhere.
 */
public final class LuhnValidator {

    private LuhnValidator() {
    }

    public static boolean isValid(String digits) {
        if (digits == null || digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
