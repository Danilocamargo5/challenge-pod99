package com.pod99.limits.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/v1/limites")
@RequiredArgsConstructor
public class LimitController {
    
    private final DynamoDbClient dynamoDbClient;
    private final ObjectMapper objectMapper;
    private final String LIMITS_TABLE = "pod99-limits";
    
    @PostMapping("/{idContrato}/reservar")
    public ReserveResponse reservarLimite(
            @PathVariable String idContrato,
            @RequestBody ReserveRequest request) {
        
        log.info("📋 POST /limites/{}/reservar | valor={}", idContrato, request.getValor());
        
        try {
            // Buscar limite atual do contrato
            GetItemRequest getRequest = GetItemRequest.builder()
                    .tableName(LIMITS_TABLE)
                    .key(Map.of("id", AttributeValue.builder().s(idContrato).build()))
                    .build();
            
            GetItemResponse getResponse = dynamoDbClient.getItem(getRequest);
            
            Double saldoAtual = 10000.0; // DEFAULT se não existir
            if (getResponse.item() != null && getResponse.item().containsKey("saldo")) {
                saldoAtual = Double.parseDouble(
                    getResponse.item().get("saldo").n()
                );
            }
            
            Double saldoAnterior = saldoAtual;
            
            // Validar se tem saldo
            if (saldoAtual < request.getValor()) {
                log.warn("❌ Limite insuficiente: saldo={}, solicitado={}", saldoAtual, request.getValor());
                throw new LimitException("Limite insuficiente");
            }
            
            // Reservar (subtrair do saldo)
            Double novoSaldo = saldoAtual - request.getValor();
            
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("id", AttributeValue.builder().s(idContrato).build());
            item.put("saldo", AttributeValue.builder().n(novoSaldo.toString()).build());
            item.put("reservado", AttributeValue.builder().n(request.getValor().toString()).build());
            
            PutItemRequest putRequest = PutItemRequest.builder()
                    .tableName(LIMITS_TABLE)
                    .item(item)
                    .build();
            
            dynamoDbClient.putItem(putRequest);
            
            log.info("✅ Limite reservado: saldoAnterior={}, valor={}, saldoAtual={}", 
                saldoAnterior, request.getValor(), novoSaldo);
            
            return new ReserveResponse(
                idContrato,
                saldoAnterior,
                novoSaldo,
                request.getValor()
            );
            
        } catch (LimitException e) {
            log.error("❌ Erro de negócio: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("❌ Erro ao reservar limite", e);
            throw new RuntimeException("Falha ao reservar", e);
        }
    }
    
    public static class ReserveRequest {
        private Double valor;
        private String idempotencyKey;
        
        public Double getValor() { return valor; }
        public void setValor(Double valor) { this.valor = valor; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }
    
    public static class ReserveResponse {
        private String id;
        private Double saldoAnterior;
        private Double saldoAtual;
        private Double reservado;
        
        public ReserveResponse(String id, Double saldoAnterior, Double saldoAtual, Double reservado) {
            this.id = id;
            this.saldoAnterior = saldoAnterior;
            this.saldoAtual = saldoAtual;
            this.reservado = reservado;
        }
        
        public String getId() { return id; }
        public Double getSaldoAnterior() { return saldoAnterior; }
        public Double getSaldoAtual() { return saldoAtual; }
        public Double getReservado() { return reservado; }
    }
    
    public static class LimitException extends RuntimeException {
        public LimitException(String message) {
            super(message);
        }
    }
}
