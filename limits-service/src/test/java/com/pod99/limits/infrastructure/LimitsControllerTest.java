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
        String requestBody = "{\"valor\": 100.0, \"idempotencyKey\": \"teste-123\"}";
        
        mockMvc.perform(post("/v1/limites/{idContrato}/reservar", idContrato)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idContrato))
                .andExpect(jsonPath("$.reservado").value(100.0));
    }
}
