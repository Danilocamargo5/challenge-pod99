package com.pod99.common.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountIdTest {

    @Test
    void shouldCreateValidAccountId() {
        AccountId accountId = new AccountId("ACC-001");

        assertEquals("ACC-001", accountId.getValue());
        assertEquals("ACC-001", accountId.toString());
    }

    @Test
    void shouldRejectNullAccountId() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new AccountId(null)
                );

        assertEquals(
                "Account ID não pode ser vazio",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectBlankAccountId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AccountId("   ")
        );
    }

    @Test
    void shouldRejectInvalidFormat() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AccountId("INVALID-001")
        );
    }

    @Test
    void shouldRejectAccountZero() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AccountId("ACC-000")
        );
    }

    @Test
    void shouldAcceptMaximumAccount() {
        AccountId accountId = new AccountId("ACC-999");

        assertEquals("ACC-999", accountId.getValue());
    }

    @Test
    void shouldCompareAccountIds() {
        AccountId account1 = new AccountId("ACC-001");
        AccountId account2 = new AccountId("ACC-001");
        AccountId account3 = new AccountId("ACC-002");

        assertEquals(account1, account1);
        assertEquals(account1, account2);
        assertNotEquals(account1, account3);
        assertNotEquals(account1, null);
        assertNotEquals(account1, "ACC-001");
        assertEquals(account1.hashCode(), account2.hashCode());
    }
}