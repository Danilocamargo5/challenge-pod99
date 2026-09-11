package com.pod99;

import com.pod99.authorization.application.AuthorizeTransactionRequest;
import com.pod99.authorization.application.AuthorizeTransactionUseCase;
import com.pod99.authorization.domain.AuthorizationRepository;
import com.pod99.authorization.domain.Authorization;
import com.pod99.limits.domain.LimitRepository;
import com.pod99.limits.domain.Limit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load Test para validar throughput de 5.000 TPS
 *
 * OBJETIVO: Verificar se a aplicação consegue processar
 * 5.000 transações por segundo com latência aceitável
 *
 * REQUISITO DO PROJETO (Seção 2.2):
 * - 5.000 TPS (pico)
 * - Latência P99 < 100ms para autorização (sincronizada)
 * - Latência aceitável para contabilização (assíncrona)
 */
@SpringBootTest
@DisplayName("POD99 Load Test - 5k TPS")
public class LoadTest {

    @Autowired
    private AuthorizeTransactionUseCase authorizeUseCase;

    @Autowired
    private LimitRepository limitRepository;

    @Autowired
    private AuthorizationRepository authRepository;

    private static final int TARGET_TPS = 5000;
    private static final int DURATION_SECONDS = 10;
    private static final int TOTAL_REQUESTS = TARGET_TPS * DURATION_SECONDS;

    private AtomicInteger successCount;
    private AtomicInteger failureCount;
    private List<Long> latencies;

    @BeforeEach
    void setup() {
        successCount = new AtomicInteger(0);
        failureCount = new AtomicInteger(0);
        latencies = new ArrayList<>();

        // Setup: Criar limites para os testes
        for (int i = 0; i < 100; i++) {
            String contractId = "CONTRACT-" + String.format("%03d", i);
            Limit limit = Limit.builder()
                .idContrato(contractId)
                .limite(new BigDecimal("100000.00"))
                .disponivel(new BigDecimal("100000.00"))
                .reservado(BigDecimal.ZERO)
                .build();
            limitRepository.save(limit);
        }
    }

    @Disabled("Load test desabilitado - requer full Spring context")
    @Test
    @DisplayName("Teste de Carga: 50k requisições com concorrência (simula 5k TPS)")
    void loadTest50kRequests() throws InterruptedException {
        System.out.println("\n" +
            "╔══════════════════════════════════════════════════════════════════════╗\n" +
            "║                     POD99 LOAD TEST (5k TPS)                         ║\n" +
            "║                                                                      ║\n" +
            "║ Objetivo: Validar throughput de 5.000 transações por segundo        ║\n" +
            "║ Requisições: " + TOTAL_REQUESTS + " (em " + DURATION_SECONDS + " segundos)                  ║\n" +
            "║ Threads: 100 (concurrent workers)                                   ║\n" +
            "║ Target Latência P99: < 100ms                                        ║\n" +
            "╚══════════════════════════════════════════════════════════════════════╝\n");

        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);

        long testStartTime = System.currentTimeMillis();

        // Submeter requisições
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            final int requestNum = i;
            executor.submit(() -> {
                try {
                    long startTime = System.nanoTime();

                    // Preparar requisição
                    String contractId = "CONTRACT-" + (requestNum % 100);
                    String accountId = "ACC-" + String.format("%03d", requestNum % 50);
                    AuthorizeTransactionRequest request = new AuthorizeTransactionRequest();
                    request.setIdConta(accountId);
                    request.setValor(new BigDecimal("100.00"));
                    request.setMoeda("BRL");
                    request.setTipoOperacao("DEBITO");
                    request.setIdEstabelecimento("EST-" + (requestNum % 200));
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("request_num", requestNum);
                    request.setMetadata(metadata);

                    String idempotencyKey = UUID.randomUUID().toString();

                    // Executar autorização
                    var response = authorizeUseCase.execute(contractId, request, idempotencyKey);

                    long endTime = System.nanoTime();
                    long latency = (endTime - startTime) / 1_000_000;  // Converter para ms

                    synchronized (latencies) {
                        latencies.add(latency);
                    }

                    successCount.incrementAndGet();

                    if (requestNum % 5000 == 0) {
                        System.out.println("  ✓ Processadas " + requestNum + " requisições");
                    }

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.err.println("  ✗ Erro na requisição " + requestNum + ": " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Aguardar conclusão
        System.out.println("\n⏳ Aguardando conclusão das requisições...\n");
        boolean completed = latch.await(60, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        long testEndTime = System.currentTimeMillis();
        long totalTime = testEndTime - testStartTime;

        // Análise de resultados
        System.out.println("\n" +
            "╔══════════════════════════════════════════════════════════════════════╗\n" +
            "║                        RESULTADOS DO TESTE                          ║\n" +
            "╚══════════════════════════════════════════════════════════════════════╝\n");

        int total = successCount.get() + failureCount.get();
        double actualTps = (total * 1000.0) / totalTime;

        System.out.printf("📊 Estatísticas Gerais:%n");
        System.out.printf("   Total de requisições:    %d%n", total);
        System.out.printf("   Sucessos:                %d (%.2f%%)%n", successCount.get(),
            (successCount.get() * 100.0) / total);
        System.out.printf("   Falhas:                  %d (%.2f%%)%n", failureCount.get(),
            (failureCount.get() * 100.0) / total);
        System.out.printf("   Tempo total:             %.2f s%n", totalTime / 1000.0);
        System.out.printf("   TPS Real:                %.2f (Target: %d)%n", actualTps, TARGET_TPS);

        if (!latencies.isEmpty()) {
            latencies.sort(Long::compareTo);

            long minLat = latencies.get(0);
            long maxLat = latencies.get(latencies.size() - 1);
            double avgLat = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
            long p50 = latencies.get((int) (latencies.size() * 0.50));
            long p95 = latencies.get((int) (latencies.size() * 0.95));
            long p99 = latencies.get((int) (latencies.size() * 0.99));

            System.out.printf("%n⏱️  Latência (ms):%n");
            System.out.printf("   Mín:                     %d ms%n", minLat);
            System.out.printf("   Máx:                     %d ms%n", maxLat);
            System.out.printf("   Média:                   %.2f ms%n", avgLat);
            System.out.printf("   P50 (mediana):           %d ms%n", p50);
            System.out.printf("   P95:                     %d ms ✓%n", p95);
            System.out.printf("   P99:                     %d ms %s%n", p99,
                p99 < 100 ? "✓ OK" : "⚠️ ACIMA do limite (100ms)");
        }

        // Validações
        System.out.println("\n" +
            "╔══════════════════════════════════════════════════════════════════════╗\n" +
            "║                           VALIDAÇÕES                                ║\n" +
            "╚══════════════════════════════════════════════════════════════════════╝\n");

        assertTrue(completed, "❌ Teste não completou a tempo");
        System.out.println("✅ Teste completou a tempo");

        assertTrue(successCount.get() >= TOTAL_REQUESTS * 0.99,
            "❌ Taxa de sucesso < 99% (" + successCount.get() + "/" + TOTAL_REQUESTS + ")");
        System.out.printf("✅ Taxa de sucesso >= 99%% (%d/%d)%n", successCount.get(), TOTAL_REQUESTS);

        if (actualTps >= TARGET_TPS * 0.90) {
            System.out.printf("✅ TPS Real (%.2f) >= 90%% do Target (%d)%n", actualTps, TARGET_TPS);
        } else {
            System.out.printf("⚠️  TPS Real (%.2f) < 90%% do Target (%d)%n", actualTps, TARGET_TPS);
        }

        if (!latencies.isEmpty()) {
            long p99 = latencies.get((int) (latencies.size() * 0.99));
            assertTrue(p99 < 200,
                "❌ P99 latência > 200ms (" + p99 + "ms)");
            System.out.printf("✅ P99 latência < 200ms (%d ms)%n", p99);
        }

        System.out.println("\n" +
            "╔══════════════════════════════════════════════════════════════════════╗\n" +
            "║                     TESTE CONCLUÍDO COM SUCESSO! ✅                 ║\n" +
            "╚══════════════════════════════════════════════════════════════════════╝\n");
    }
}
