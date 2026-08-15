package com.bank.gateway.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EncryptedStringConverterTest {

    private static final String TEST_KEY = "mzD/i0fiHcYLv3g0wwTlvAp49wYo1MFNRYBM/f52XPI=";

    private final EncryptedStringConverter converter = new EncryptedStringConverter(TEST_KEY);

    @Test
    void encryptsThenDecryptsBackToTheOriginalPlaintext() {
        String ciphertext = converter.convertToDatabaseColumn("Alice Johnson");
        assertNotEquals("Alice Johnson", ciphertext);
        assertEquals("Alice Johnson", converter.convertToEntityAttribute(ciphertext));
    }

    @Test
    void encryptingTheSamePlaintextTwiceProducesDifferentCiphertext() {
        String first = converter.convertToDatabaseColumn("Alice Johnson");
        String second = converter.convertToDatabaseColumn("Alice Johnson");

        assertNotEquals(first, second, "random IV should make repeated encryptions differ");
        assertEquals("Alice Johnson", converter.convertToEntityAttribute(first));
        assertEquals("Alice Johnson", converter.convertToEntityAttribute(second));
    }

    @Test
    void nullPassesThroughUnchangedInBothDirections() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }
}
