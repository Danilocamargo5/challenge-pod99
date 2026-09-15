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
        // Arrange
        String idContrato = "CONTA-001";
        String requestBody = "{\"valor\": 100.0, \"idempotencyKey\": \"teste-123\"}";
        
        // Act & Assert
        mockMvc.perform(post("/v1/limites/{idContrato}/reservar", idContrato)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idContrato))
                .andExpect(jsonPath("$.reservado").value(100.0))
                .andExpect(jsonPath("$.saldoAtual").value(9900.0));
    }
    
    @Test
    void testHealth() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/v1/limites/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("limits-service"))
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
