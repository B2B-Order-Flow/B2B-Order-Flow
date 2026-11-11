package com.b2borderflow.core.company;

import org.springframework.stereotype.Component;

/**
 * Проверка контрольной суммы ИНН до обращения к справочнику.
 * 10 цифр — организация, 12 — ИП; неверная сумма даёт 422 без вызова мока
 * (docs/system/integrations.md, 4.3).
 */
@Component
public class InnValidator {

    private static final int[] WEIGHTS_10 = {2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] WEIGHTS_11 = {7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] WEIGHTS_12 = {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8};

    public boolean isValid(String inn) {
        if (inn == null) {
            return false;
        }
        String value = inn.trim();
        if (!value.matches("\\d{10}|\\d{12}")) {
            return false;
        }

        if (value.length() == 10) {
            return checksum(value, WEIGHTS_10) == digit(value, 9);
        }

        return checksum(value, WEIGHTS_11) == digit(value, 10)
                && checksum(value, WEIGHTS_12) == digit(value, 11);
    }

    private static int checksum(String value, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += Character.digit(value.charAt(i), 10) * weights[i];
        }
        return sum % 11 % 10;
    }

    private static int digit(String value, int index) {
        return Character.digit(value.charAt(index), 10);
    }
}
