package com.pod99.limits.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LimitsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testReservarLimite_Success() throws Exception {

        String idContrato = "CONTA-001";

        String requestBody =
                "{\"valor\":100.0,\"idempotencyKey\":\"teste-success\"}";

        mockMvc.perform(
                        post("/v1/limites/{idContrato}/reservar", idContrato)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idContrato))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reservado").value(100.0));
    }

    @Test
    void testReservarLimite_InsufficientLimit() throws Exception {

        String idContrato = "CONTA-001";

        String requestBody =
                "{\"valor\":999999.0,\"idempotencyKey\":\"teste-insuficiente\"}";

        mockMvc.perform(
                        post("/v1/limites/{idContrato}/reservar", idContrato)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.id").value(idContrato))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.erro").value("Limite insuficiente"));
    }

    @Test
    void testReservarLimite_InvalidContract() throws Exception {

        String idContrato = "CONTA-INEXISTENTE";

        String requestBody =
                "{\"valor\":100.0,\"idempotencyKey\":\"teste-inexistente\"}";

        mockMvc.perform(
                        post("/v1/limites/{idContrato}/reservar", idContrato)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.id").value(idContrato))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.erro").value("Contrato não encontrado"));
    }

    @Test
    void testHealth() throws Exception {

        mockMvc.perform(get("/v1/limites/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("limits-service"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.port").value(8082));
    }
}