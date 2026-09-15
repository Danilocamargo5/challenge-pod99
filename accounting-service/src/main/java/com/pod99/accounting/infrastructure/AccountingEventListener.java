package com.pod99.accounting.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pod99.accounting.application.AccountingService;
import com.pod99.accounting.domain.AccountingRecord;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingEventListener {

    private final ObjectMapper objectMapper;
    private final AccountingService accountingService;

    @SqsListener("pod99-accounting-queue.fifo")
    public void handleTransacaoAutorizada(String message) {

        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│ 📨 ACCOUNTING SERVICE - Recebido evento do SQS                  │");
        log.info("├─────────────────────────────────────────────────────────────────┤");

        try {
            // EventBridge envolve o evento original dentro de "detail".
            JsonNode root = objectMapper.readTree(message);

            JsonNode event = root.has("detail")
                ? root.get("detail")
                : root;

            String idAutorizacao = event.path("id").asText();

            String idContrato = event.path("idContrato").asText();

            String idConta = event.path("idConta").asText();

            BigDecimal valor = event.path("valor").decimalValue();

            String moeda = event.path("moeda").asText("BRL");

            log.info(
                "│ Autorização: {} | Contrato: {}",
                idAutorizacao,
                idContrato
            );

            log.info(
                "│ Conta: {} | Valor: {} {}",
                idConta,
                valor,
                moeda
            );

            AccountingRecord record = AccountingRecord.processed(
                idAutorizacao, // event_id
                idAutorizacao,
                idContrato,
                idConta,
                valor,
                moeda
            );

            accountingService.process(record);

            log.info("├─────────────────────────────────────────────────────────────────┤");
            log.info("│ Status: PROCESSADO E PERSISTIDO ✅                               │");
            log.info("└─────────────────────────────────────────────────────────────────┘");

        } catch (Exception e) {
            log.error("❌ Erro ao processar evento SQS", e);
            throw new RuntimeException("Falha ao processar evento", e);
        }
    }
}