package com.pod99.authorization.application;

import com.pod99.authorization.domain.Authorization;
import com.pod99.authorization.domain.AuthorizationRepository;
import com.pod99.authorization.domain.AuthorizationStatus;
import com.pod99.common.exception.InsufficientLimitException;
import com.pod99.common.exception.LockAcquisitionException;
import com.pod99.config.EventBridgePublisher;
import com.pod99.config.LockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizeTransactionUseCaseTest {

    @Mock
    private EventBridgePublisher eventPublisher;

    @Mock
    private LockService lockService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AuthorizationRepository authorizationRepository;

    @InjectMocks
    private AuthorizeTransactionUseCase useCase;

    private AuthorizeTransactionRequest createValidRequest() {
        AuthorizeTransactionRequest request =
                new AuthorizeTransactionRequest();

        request.setIdConta("ACC-001");
        request.setValor(BigDecimal.valueOf(100.00));
        request.setMoeda("BRL");
        request.setTipoOperacao("DEBITO");

        return request;
    }

    private AuthorizeTransactionUseCase.LimitReserveResponse
    createLimitResponse(String idContrato) {

        AuthorizeTransactionUseCase.LimitReserveResponse response =
                new AuthorizeTransactionUseCase.LimitReserveResponse();

        response.setId(idContrato);
        response.setSaldoAtual(9900.0);
        response.setSaldoAnterior(10000.0);
        response.setReservado(100.0);

        return response;
    }

    @Test
    void testAuthorizeTransaction_Success() {

        String idContrato = "CONTA-001";
        String idempotencyKey = "teste-123";

        AuthorizeTransactionRequest request = createValidRequest();

        when(authorizationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(lockService.acquireTransactionLocks(
                anyString(),
                eq(idContrato)))
                .thenReturn(Arrays.asList("lock1", "lock2"));

        when(restTemplate.postForObject(
                anyString(),
                any(),
                eq(AuthorizeTransactionUseCase.LimitReserveResponse.class)))
                .thenReturn(createLimitResponse(idContrato));

        AuthorizeTransactionResponse response =
                useCase.execute(
                        idContrato,
                        request,
                        idempotencyKey
                );

        assertNotNull(response);
        assertEquals("APPROVED", response.getStatus());
        assertEquals(100.0, response.getSaldoReservado());
        assertFalse(response.isRepetition());

        verify(authorizationRepository)
                .findByIdempotencyKey(idempotencyKey);

        verify(authorizationRepository)
                .save(any(Authorization.class));

        verify(lockService)
                .acquireTransactionLocks(
                        anyString(),
                        eq(idContrato));

        verify(restTemplate)
                .postForObject(
                        anyString(),
                        any(),
                        eq(AuthorizeTransactionUseCase.LimitReserveResponse.class));

        verify(lockService)
                .releaseLocks(anyList());

        verify(eventPublisher)
                .publishTransactionAuthorized(
                        anyString(),
                        eq(idContrato),
                        eq("ACC-001"),
                        eq(100.0),
                        eq("BRL"));
    }

    @Test
    void testAuthorizeTransaction_Idempotency() {

        String idContrato = "CONTA-001";
        String idempotencyKey = "teste-123";

        AuthorizeTransactionRequest request = createValidRequest();

        Authorization existing = Authorization.builder()
                .idAutorizacao("AUTH-EXISTENTE")
                .idempotencyKey(idempotencyKey)
                .idContrato(idContrato)
                .idConta("ACC-001")
                .valor(BigDecimal.valueOf(100.00))
                .saldoReservado(BigDecimal.valueOf(100.00))
                .status(AuthorizationStatus.APPROVED)
                .criadoEm(LocalDateTime.now())
                .build();

        when(authorizationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existing));

        AuthorizeTransactionResponse response =
                useCase.execute(
                        idContrato,
                        request,
                        idempotencyKey
                );

        assertNotNull(response);
        assertEquals(
                "AUTH-EXISTENTE",
                response.getAuthorizationId());

        assertEquals(
                "APPROVED",
                response.getStatus());

        assertTrue(response.isRepetition());

        verify(authorizationRepository)
                .findByIdempotencyKey(idempotencyKey);

        verifyNoInteractions(lockService);
        verifyNoInteractions(restTemplate);
        verifyNoInteractions(eventPublisher);

        verify(authorizationRepository, never())
                .save(any());
    }

    @Test
    void testAuthorizeTransaction_InvalidValue() {

        String idContrato = "CONTA-001";
        String idempotencyKey = "teste-invalid-value";

        AuthorizeTransactionRequest request = createValidRequest();
        request.setValor(BigDecimal.valueOf(-100.00));

        when(authorizationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(lockService.acquireTransactionLocks(
                anyString(),
                eq(idContrato)))
                .thenReturn(Arrays.asList("lock1", "lock2"));

        assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(
                        idContrato,
                        request,
                        idempotencyKey
                )
        );

        verify(lockService)
                .releaseLocks(anyList());

        verify(authorizationRepository, never())
                .save(any());

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void testAuthorizeTransaction_ZeroValue() {

        String idContrato = "CONTA-001";
        String idempotencyKey = "teste-zero-value";

        AuthorizeTransactionRequest request = createValidRequest();
        request.setValor(BigDecimal.ZERO);

        when(authorizationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(lockService.acquireTransactionLocks(
                anyString(),
                eq(idContrato)))
                .thenReturn(Arrays.asList("lock1", "lock2"));

        assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(
                        idContrato,
                        request,
                        idempotencyKey
                )
        );

        verify(lockService)
                .releaseLocks(anyList());

        verify(authorizationRepository, never())
                .save(any());

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void testAuthorizeTransaction_NullValue() {

        String idContrato = "CONTA-001";
        String idempotencyKey = "teste-null-value";

        AuthorizeTransactionRequest request = createValidRequest();
        request.setValor(null);

        when(authorizationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(lockService.acquireTransactionLocks(
                anyString(),
                eq(idContrato)))
                .thenReturn(Arrays.asList("lock1", "lock2"));

        assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(
                        idContrato,
                        request,
                        idempotencyKey
                )
        );

        verify(lockService)
                .releaseLocks(anyList());

        verify(authorizationRepository, never())
                .save(any());

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void testAuthorizeTransaction_InsufficientLimit() {

        String idContrato = "CONTA-001";
        String idempotencyKey = "teste-insufficient-limit";

        AuthorizeTransactionRequest request = createValidRequest();

        when(authorizationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(lockService.acquireTransactionLocks(
                anyString(),
                eq(idContrato)))
                .thenReturn(Arrays.asList("lock1", "lock2"));

        when(restTemplate.postForObject(
                anyString(),
                any(),
                eq(AuthorizeTransactionUseCase.LimitReserveResponse.class)))
                .thenThrow(
                        new RuntimeException(
                                "402 Payment Required"
                        )
                );

        assertThrows(
                InsufficientLimitException.class,
                () -> useCase.execute(
                        idContrato,
                        request,
                        idempotencyKey
                )
        );

        verify(lockService)
                .releaseLocks(anyList());

        verify(authorizationRepository, never())
                .save(any());

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void testAuthorizeTransaction_InvalidContract404() {

        String idContrato = "CONTA-999";
        String idempotencyKey = "teste-invalid-contract";

        AuthorizeTransactionRequest request = createValidRequest();

        when(authorizationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(lockService.acquireTransactionLocks(
                anyString(),
                eq(idContrato)))
                .thenReturn(Arrays.asList("lock1", "lock2"));

        when(restTemplate.postForObject(
                anyString(),
                any(),
                eq(AuthorizeTransactionUseCase.LimitReserveResponse.class)))
                .thenThrow(
                        new RuntimeException(
                                "404 Not Found"
                        )
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> useCase.execute(
                                idContrato,
                                request,
                                idempotencyKey
                        )
                );

        assertTrue(
                exception.getMessage()
                        .contains("Contrato inválido"));

        verify(lockService)
                .releaseLocks(anyList());

        verify(authorizationRepository, never())
                .save(any());

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void testAuthorizeTransaction_InvalidContract422() {

        String idContrato = "CONTA-999";
        String idempotencyKey = "teste-invalid-contract-422";

        AuthorizeTransactionRequest request = createValidRequest();

        when(authorizationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(lockService.acquireTransactionLocks(
                anyString(),
                eq(idContrato)))
                .thenReturn(Arrays.asList("lock1", "lock2"));

        when(restTemplate.postForObject(
                anyString(),
                any(),
                eq(AuthorizeTransactionUseCase.LimitReserveResponse.class)))
                .thenThrow(
                        new RuntimeException(
                                "422 Unprocessable Entity"
                        )
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> useCase.execute(
                        idContrato,
                        request,
                        idempotencyKey
                )
        );

        verify(lockService)
                .releaseLocks(anyList());

        verify(authorizationRepository, never())
                .save(any());

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void testAuthorizeTransaction_LockAcquisitionFailure() {

        String idContrato = "CONTA-001";
        String idempotencyKey = "teste-lock-conflict";

        AuthorizeTransactionRequest request = createValidRequest();

        when(authorizationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(lockService.acquireTransactionLocks(
                anyString(),
                eq(idContrato)))
                .thenThrow(
                        new LockAcquisitionException(
                                "Não foi possível adquirir lock"
                        )
                );

        assertThrows(
                LockAcquisitionException.class,
                () -> useCase.execute(
                        idContrato,
                        request,
                        idempotencyKey
                )
        );

        verify(lockService, never())
                .releaseLocks(anyList());

        verifyNoInteractions(restTemplate);
        verifyNoInteractions(eventPublisher);

        verify(authorizationRepository, never())
                .save(any());
    }

    @Test
    void testAuthorizeTransaction_UnexpectedLimitsFailure() {

        String idContrato = "CONTA-001";
        String idempotencyKey = "teste-limits-error";

        AuthorizeTransactionRequest request = createValidRequest();

        when(authorizationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        when(lockService.acquireTransactionLocks(
                anyString(),
                eq(idContrato)))
                .thenReturn(Arrays.asList("lock1", "lock2"));

        when(restTemplate.postForObject(
                anyString(),
                any(),
                eq(AuthorizeTransactionUseCase.LimitReserveResponse.class)))
                .thenThrow(
                        new RuntimeException(
                                "Connection refused"
                        )
                );

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> useCase.execute(
                                idContrato,
                                request,
                                idempotencyKey
                        )
                );

        assertNotNull(exception);

        verify(lockService)
                .releaseLocks(anyList());

        verify(authorizationRepository, never())
                .save(any());

        verifyNoInteractions(eventPublisher);
    }
}