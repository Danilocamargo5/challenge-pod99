package com.pod99.authorization.application;

import com.pod99.authorization.domain.Authorization;
import com.pod99.authorization.domain.AuthorizationRepository;
import com.pod99.common.exception.InsufficientLimitException;
import com.pod99.common.exception.LockAcquisitionException;
import com.pod99.config.EventBridgePublisher;
import com.pod99.config.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizeTransactionUseCase {

    private final EventBridgePublisher eventPublisher;
    private final LockService lockService;
    private final RestTemplate restTemplate;
    private final AuthorizationRepository authorizationRepository;

    public AuthorizeTransactionResponse execute(
            String idContrato,
            AuthorizeTransactionRequest request,
            String idempotencyKey) {

        String correlationId = MDC.get("X-Correlation-ID");
        String traceId = MDC.get("X-Trace-ID");

        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│ 🔐 AUTHORIZATION SERVICE - Iniciando autorização                │");
        log.info("├─────────────────────────────────────────────────────────────────┤");
        log.info("│ Contrato: {} | Conta: {}", idContrato, request.getIdConta());
        log.info(
                "│ Valor: {} {} | Correlação: {}",
                request.getValor(),
                request.getMoeda(),
                correlationId
        );
        log.info(
                "│ Trace: {} | Idempotency: {}",
                traceId,
                idempotencyKey
        );
        log.info("└─────────────────────────────────────────────────────────────────┘");

        /*
         * ==========================================================
         * IDEMPOTÊNCIA
         * ==========================================================
         *
         * Se a requisição já foi processada anteriormente:
         *
         * - não reserva limite novamente
         * - não cria nova autorização
         * - não publica novo evento
         */
        var existingAuthorization =
                authorizationRepository.findByIdempotencyKey(idempotencyKey);

        if (existingAuthorization.isPresent()) {

            Authorization existing = existingAuthorization.get();

            log.info(
                    "🔄 Requisição idempotente encontrada | key={} | authorizationId={}",
                    idempotencyKey,
                    existing.getIdAutorizacao()
            );

            return new AuthorizeTransactionResponse(
                    existing.getIdAutorizacao(),
                    existing.getStatus().toString(),
                    "Transação autorizada com sucesso",
                    existing.getIdAutorizacao(),
                    existing.getSaldoReservado().doubleValue(),
                    true
            );
        }

        List<String> acquiredLocks = null;

        Double valor = request.getValor() != null
                ? request.getValor().doubleValue()
                : null;

        try {

            /*
             * ======================================================
             * 1 - ADQUIRIR LOCKS
             * ======================================================
             */

            log.info("  ➜ [1/5] 🔒 Adquirindo locks...");

            String lockOwnerId =
                    java.util.UUID.randomUUID().toString();

            acquiredLocks =
                    lockService.acquireTransactionLocks(
                            lockOwnerId,
                            idContrato
                    );

            log.info(
                    "  ✅ [1/5] Locks adquiridos: {}",
                    acquiredLocks
            );

            /*
             * ======================================================
             * VALIDAÇÕES
             * ======================================================
             */

            if (idContrato == null || idContrato.isBlank()) {

                log.warn("  ❌ Contrato inválido: {}", idContrato);

                throw new IllegalArgumentException(
                        "Contrato inválido"
                );
            }

            if (valor == null || valor <= 0.0) {

                log.warn("  ❌ Valor inválido: {}", valor);

                throw new IllegalArgumentException(
                        "Valor inválido"
                );
            }

            /*
             * ======================================================
             * 2 - LIMITS SERVICE
             * ======================================================
             */

            log.info(
                    "  ➜ [2/5] 📞 Chamando LIMITS SERVICE..."
            );

            String limitsUrl =
                    "http://localhost:8082/v1/limites/"
                            + idContrato
                            + "/reservar";

            LimitReserveRequest reserveRequest =
                    new LimitReserveRequest();

            reserveRequest.setValor(valor);
            reserveRequest.setIdempotencyKey(idempotencyKey);

            LimitReserveResponse response;

            try {

                response = restTemplate.postForObject(
                        limitsUrl,
                        reserveRequest,
                        LimitReserveResponse.class
                );

                log.info(
                        "  ✅ [2/5] LIMITS SERVICE respondeu - Saldo: {}",
                        response.getSaldoAtual()
                );

            } catch (Exception e) {

                log.error(
                        "  ❌ [2/5] Erro ao chamar LIMITS SERVICE: {}",
                        e.getMessage()
                );

                /*
                 * LIMITES INSUFICIENTES
                 *
                 * Limits Service retorna HTTP 402.
                 */
                if (e.getMessage() != null
                        && e.getMessage().contains("402")) {

                    throw new InsufficientLimitException(
                            "Limite insuficiente"
                    );
                }

                /*
                 * CONTRATO INVÁLIDO / INEXISTENTE
                 *
                 * Se o Limits Service responder 404 ou 422,
                 * transformamos em erro de validação.
                 *
                 * O Controller converterá IllegalArgumentException
                 * para HTTP 422.
                 */
                if (e.getMessage() != null
                        && (e.getMessage().contains("404")
                        || e.getMessage().contains("422"))) {

                    throw new IllegalArgumentException(
                            "Contrato inválido: " + idContrato
                    );
                }

                throw new RuntimeException(
                        "Falha ao reservar limite",
                        e
                );
            }

            /*
             * ======================================================
             * 3 - CRIAR AUTORIZAÇÃO
             * ======================================================
             */

            Authorization authorization =
                    Authorization.criar(
                            idContrato,
                            request.getIdConta(),
                            request.getValor(),
                            request.getMoeda(),
                            request.getTipoOperacao(),
                            request.getIdEstabelecimento(),
                            request.getMetadata(),
                            BigDecimal.valueOf(
                                    response.getReservado()
                            ),
                            correlationId,
                            idempotencyKey
                    );

            log.info(
                    "  ➜ [3/5] 💾 Persistindo autorização - ID: {}",
                    authorization.getIdAutorizacao()
            );

            authorizationRepository.save(
                    authorization
            );

            log.info(
                    "  ✅ [3/5] Autorização persistida"
            );

            /*
             * ======================================================
             * 4 - EVENTBRIDGE
             * ======================================================
             */

            log.info(
                    "  ➜ [4/5] 📤 Publicando evento no EventBridge..."
            );

            eventPublisher.publishTransactionAuthorized(
                    authorization.getIdAutorizacao(),
                    idContrato,
                    request.getIdConta(),
                    valor,
                    request.getMoeda()
            );

            log.info(
                    "  ✅ [4/5] Evento publicado"
            );

            log.info(
                    "  ✅ [5/5] Fluxo completado com sucesso"
            );

            log.info("┌─────────────────────────────────────────────────────────────────┐");
            log.info("│ 🟢 AUTHORIZATION SERVICE - Autorização APROVADA                 │");
            log.info("├─────────────────────────────────────────────────────────────────┤");

            log.info(
                    "│ ID Autorização: {} | Status: APPROVED",
                    authorization.getIdAutorizacao()
            );

            log.info(
                    "│ Saldo Reservado: {} {} | Correlação: {}",
                    authorization.getSaldoReservado(),
                    request.getMoeda(),
                    correlationId
            );

            log.info("└─────────────────────────────────────────────────────────────────┘");

            return new AuthorizeTransactionResponse(
                    authorization.getIdAutorizacao(),
                    "APPROVED",
                    "Transação autorizada com sucesso",
                    authorization.getIdAutorizacao(),
                    authorization
                            .getSaldoReservado()
                            .doubleValue(),
                    false
            );

        /*
         * ==========================================================
         * EXCEÇÕES DE NEGÓCIO
         * ==========================================================
         */

        } catch (LockAcquisitionException e) {

            log.warn(
                    "  ⚠️ Conflito de concorrência - lock não adquirido"
            );

            throw e;

        } catch (InsufficientLimitException e) {

            log.warn(
                    "  ⚠️ Limite insuficiente"
            );

            throw e;

        /*
         * IMPORTANTE PARA O TESTE 8
         *
         * Não transformar IllegalArgumentException em RuntimeException.
         *
         * Ela precisa chegar intacta ao AuthorizationController,
         * que já faz:
         *
         * IllegalArgumentException
         *          ↓
         * HTTP 422 UNPROCESSABLE_ENTITY
         */
        } catch (IllegalArgumentException e) {

            log.warn(
                    "  ⚠️ Erro de validação: {}",
                    e.getMessage()
            );

            throw e;

        } catch (Exception e) {

            log.error(
                    "  ❌ Erro ao autorizar transação",
                    e
            );

            throw new RuntimeException(
                    "Falha na autorização",
                    e
            );

        } finally {

            /*
             * ======================================================
             * LIBERAR LOCKS
             * ======================================================
             */

            if (acquiredLocks != null
                    && !acquiredLocks.isEmpty()) {

                lockService.releaseLocks(
                        acquiredLocks
                );

                log.info(
                        "  🔓 Locks liberados"
                );
            }
        }
    }

    /*
     * ==============================================================
     * DTO - REQUEST PARA LIMITS SERVICE
     * ==============================================================
     */

    public static class LimitReserveRequest {

        private Double valor;
        private String idempotencyKey;

        public Double getValor() {
            return valor;
        }

        public void setValor(Double valor) {
            this.valor = valor;
        }

        public String getIdempotencyKey() {
            return idempotencyKey;
        }

        public void setIdempotencyKey(
                String idempotencyKey) {

            this.idempotencyKey =
                    idempotencyKey;
        }
    }

    /*
     * ==============================================================
     * DTO - RESPONSE DO LIMITS SERVICE
     * ==============================================================
     */

    public static class LimitReserveResponse {

        private String id;
        private Double saldoAnterior;
        private Double saldoAtual;
        private Double reservado;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Double getSaldoAnterior() {
            return saldoAnterior;
        }

        public void setSaldoAnterior(
                Double saldoAnterior) {

            this.saldoAnterior =
                    saldoAnterior;
        }

        public Double getSaldoAtual() {
            return saldoAtual;
        }

        public void setSaldoAtual(
                Double saldoAtual) {

            this.saldoAtual =
                    saldoAtual;
        }

        public Double getReservado() {
            return reservado;
        }

        public void setReservado(
                Double reservado) {

            this.reservado =
                    reservado;
        }
    }
}