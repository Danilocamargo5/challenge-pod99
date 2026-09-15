package com.pod99.common.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContractIdTest {

    @Test
    void shouldCreateValidContractId() {
        ContractId contractId = new ContractId("CONTA-001");

        assertEquals("CONTA-001", contractId.getValue());
        assertEquals("CONTA-001", contractId.toString());
    }

    @Test
    void shouldRejectNullContractId() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ContractId(null)
                );

        assertEquals(
                "Contract ID não pode ser vazio",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectBlankContractId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContractId("")
        );
    }

    @Test
    void shouldRejectInvalidFormat() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContractId("CONTRATO-001")
        );
    }

    @Test
    void shouldRejectContractZero() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ContractId("CONTA-000")
        );
    }

    @Test
    void shouldAcceptMaximumContract() {
        ContractId contractId = new ContractId("CONTA-999");

        assertEquals("CONTA-999", contractId.getValue());
    }

    @Test
    void shouldCompareContractIds() {
        ContractId contract1 = new ContractId("CONTA-001");
        ContractId contract2 = new ContractId("CONTA-001");
        ContractId contract3 = new ContractId("CONTA-002");

        assertEquals(contract1, contract1);
        assertEquals(contract1, contract2);
        assertNotEquals(contract1, contract3);
        assertNotEquals(contract1, null);
        assertNotEquals(contract1, "CONTA-001");
        assertEquals(contract1.hashCode(), contract2.hashCode());
    }
}