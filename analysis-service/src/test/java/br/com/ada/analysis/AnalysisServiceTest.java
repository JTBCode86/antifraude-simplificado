package br.com.ada.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {
    @Mock
    private AnalysisRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisService(repository, eventPublisher);
    }

    @Test
    void processApprovesTransactionAtReviewThresholdAndPublishesResult() {
        UUID transactionId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1000.00");
        TransactionReceived event = transaction(transactionId, amount);
        when(repository.insertIfAbsent(
                eq(transactionId),
                eq(amount),
                eq("Loja teste"),
                eq("APPROVED"),
                eq("Valor dentro do limite didático configurado"),
                any(Instant.class)))
                .thenReturn(1);

        service.process(event);

        ArgumentCaptor<AnalysisCompleted> resultCaptor = ArgumentCaptor.forClass(AnalysisCompleted.class);
        verify(eventPublisher).publishEvent(resultCaptor.capture());
        AnalysisCompleted result = resultCaptor.getValue();
        assertEquals(transactionId, result.transactionId());
        assertEquals(amount, result.amount());
        assertEquals("APPROVED", result.status());
        assertNotNull(result.analyzedAt());
    }

    @Test
    void processFlagsTransactionAboveReviewThreshold() {
        UUID transactionId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("1000.01");
        TransactionReceived event = transaction(transactionId, amount);
        when(repository.insertIfAbsent(
                eq(transactionId),
                eq(amount),
                eq("Loja teste"),
                eq("REVIEW"),
                eq("Valor acima do limite didático configurado"),
                any(Instant.class)))
                .thenReturn(1);

        service.process(event);

        ArgumentCaptor<AnalysisCompleted> resultCaptor = ArgumentCaptor.forClass(AnalysisCompleted.class);
        verify(eventPublisher).publishEvent(resultCaptor.capture());
        assertEquals("REVIEW", resultCaptor.getValue().status());
        assertEquals("Valor acima do limite didático configurado", resultCaptor.getValue().reason());
    }

    @Test
    void processDoesNotPublishWhenTransactionWasAlreadyAnalyzed() {
        UUID transactionId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("125.50");
        when(repository.insertIfAbsent(
                eq(transactionId),
                eq(amount),
                eq("Loja teste"),
                anyString(),
                anyString(),
                any(Instant.class)))
                .thenReturn(0);

        service.process(transaction(transactionId, amount));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void findByTransactionIdReturnsStoredAnalysis() {
        UUID transactionId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("125.50");
        Instant analyzedAt = Instant.parse("2026-11-11T12:00:00Z");
        AnalysisResult stored = new AnalysisResult(
                transactionId,
                amount,
                "Loja teste",
                "APPROVED",
                "Valor dentro do limite didático configurado",
                analyzedAt);
        when(repository.findById(transactionId)).thenReturn(Optional.of(stored));

        AnalysisResponse response = service.findByTransactionId(transactionId);

        assertNotNull(response);
        assertEquals(transactionId, response.transactionId());
        assertEquals(amount, response.amount());
        assertEquals("APPROVED", response.status());
        assertEquals(analyzedAt, response.analyzedAt());
    }

    @Test
    void findByTransactionIdReturnsNullWhenAnalysisDoesNotExist() {
        UUID transactionId = UUID.randomUUID();
        when(repository.findById(transactionId)).thenReturn(Optional.empty());

        AnalysisResponse response = service.findByTransactionId(transactionId);

        assertNull(response);
        verify(repository).findById(transactionId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    private TransactionReceived transaction(UUID id, BigDecimal amount) {
        return new TransactionReceived(id, amount, "Loja teste", Instant.parse("2026-11-11T12:00:00Z"));
    }
}
