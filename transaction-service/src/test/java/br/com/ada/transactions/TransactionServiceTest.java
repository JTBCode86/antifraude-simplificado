package br.com.ada.transactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    @Mock
    private TransactionRepository repository;

    @Mock
    private TransactionPublisher publisher;

    @InjectMocks
    private TransactionService service;

    @Test
    void createPersistsTransactionAndPublishesMatchingEvent() {
        BigDecimal amount = new BigDecimal("125.50");
        String merchant = "Loja teste";

        TransactionResponse response = service.create(new TransactionRequest(amount, merchant));

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        ArgumentCaptor<TransactionReceived> eventCaptor = ArgumentCaptor.forClass(TransactionReceived.class);
        verify(repository).save(transactionCaptor.capture());
        verify(publisher).publish(eventCaptor.capture());

        Transaction transaction = transactionCaptor.getValue();
        TransactionReceived event = eventCaptor.getValue();
        assertNotNull(response.id());
        assertEquals("RECEIVED", response.status());
        assertEquals(response.id(), transaction.getId());
        assertEquals(response.id(), event.id());
        assertEquals(amount, transaction.getAmount());
        assertEquals(amount, event.amount());
        assertEquals(merchant, event.merchant());
        assertEquals(transaction.getCreatedAt(), event.createdAt());
    }

    @Test
    void createDoesNotPublishWhenPersistenceFails() {
        RuntimeException failure = new RuntimeException("database unavailable");
        doThrow(failure).when(repository).save(org.mockito.ArgumentMatchers.any(Transaction.class));

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> service.create(new TransactionRequest(new BigDecimal("125.50"), "Loja teste")));

        assertEquals(failure, thrown);
        verify(publisher, never()).publish(org.mockito.ArgumentMatchers.any(TransactionReceived.class));
    }

    @Test
    void findStatusReturnsReceivedWhenAnalysisHasNotCompleted() {
        UUID id = UUID.randomUUID();
        Transaction transaction = new Transaction(
                id,
                new BigDecimal("125.50"),
                "Loja teste",
                Instant.parse("2026-11-13T12:00:00Z"));
        when(repository.findById(id)).thenReturn(Optional.of(transaction));

        TransactionStatusResponse response = service.findStatus(id).orElseThrow();

        assertEquals(id, response.id());
        assertEquals(new BigDecimal("125.50"), response.amount());
        assertEquals("Loja teste", response.merchant());
        assertEquals("RECEIVED", response.status());
        assertNull(response.analysisStatus());
        assertNull(response.analyzedAt());
    }

    @Test
    void findRecentReturnsTransactionsNewestFirstWithTrackingDetails() {
        Transaction transaction = new Transaction(
                UUID.randomUUID(),
                new BigDecimal("250.00"),
                "Loja recente",
                Instant.parse("2026-11-13T12:00:00Z"));
        when(repository.findTop100ByOrderByCreatedAtDesc()).thenReturn(List.of(transaction));

        List<TransactionStatusResponse> responses = service.findRecent();

        assertEquals(1, responses.size());
        assertEquals(transaction.getId(), responses.get(0).id());
        assertEquals(new BigDecimal("250.00"), responses.get(0).amount());
        assertEquals("Loja recente", responses.get(0).merchant());
        assertEquals("RECEIVED", responses.get(0).status());
    }

    @Test
    void completeAnalysisUpdatesTrackedTransactionWithResult() {
        UUID id = UUID.randomUUID();
        Instant analyzedAt = Instant.parse("2026-11-13T12:01:00Z");
        Transaction transaction = new Transaction(
                id,
                new BigDecimal("125.50"),
                "Loja teste",
                Instant.parse("2026-11-13T12:00:00Z"));
        AnalysisCompleted event = new AnalysisCompleted(
                id,
                new BigDecimal("125.50"),
                "Loja teste",
                "APPROVED",
                "Valor dentro do limite didático configurado",
                analyzedAt);
        when(repository.findById(id)).thenReturn(Optional.of(transaction));

        service.completeAnalysis(event);

        verify(repository).save(transaction);
        assertEquals("ANALYZED", transaction.getStatus());
        assertEquals("APPROVED", transaction.getAnalysisStatus());
        assertEquals("Valor dentro do limite didático configurado", transaction.getAnalysisReason());
        assertEquals(analyzedAt, transaction.getAnalyzedAt());
    }

    @Test
    void completeAnalysisFailsForUnknownTransaction() {
        UUID id = UUID.randomUUID();
        AnalysisCompleted event = new AnalysisCompleted(
                id,
                new BigDecimal("125.50"),
                "Loja teste",
                "APPROVED",
                "Resultado didático",
                Instant.parse("2026-11-13T12:01:00Z"));
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> service.completeAnalysis(event));
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(Transaction.class));
    }

    @Test
    void manualReviewDecisionPreservesAutomatedResultAndRecordsOperator() {
        UUID id = UUID.randomUUID();
        Instant analyzedAt = Instant.parse("2026-11-13T12:01:00Z");
        Transaction transaction = new Transaction(
                id,
                new BigDecimal("1250.00"),
                "Loja teste",
                Instant.parse("2026-11-13T12:00:00Z"));
        transaction.completeAnalysis(new AnalysisCompleted(
                id,
                new BigDecimal("1250.00"),
                "Loja teste",
                "REVIEW",
                "Valor acima do limite",
                analyzedAt));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(transaction));

        TransactionStatusResponse response = service.decideReview(
                id,
                new ManualReviewRequest(ManualReviewDecision.APPROVED, "Ana Operadora", "Compra validada"));

        assertEquals("REVIEW", response.analysisStatus());
        assertEquals("APPROVED", response.manualDecision());
        assertEquals("Ana Operadora", response.reviewedBy());
        assertEquals("Compra validada", response.reviewReason());
        assertNotNull(response.reviewedAt());
        verify(repository).save(transaction);
    }

    @Test
    void manualReviewDecisionIsRejectedForTransactionsWithoutReviewResult() {
        UUID id = UUID.randomUUID();
        Transaction transaction = new Transaction(
                id,
                new BigDecimal("100.00"),
                "Loja teste",
                Instant.parse("2026-11-13T12:00:00Z"));
        transaction.completeAnalysis(new AnalysisCompleted(
                id,
                new BigDecimal("100.00"),
                "Loja teste",
                "APPROVED",
                "Valor dentro do limite",
                Instant.parse("2026-11-13T12:01:00Z")));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(transaction));

        assertThrows(
                IllegalStateException.class,
                () -> service.decideReview(
                        id,
                        new ManualReviewRequest(ManualReviewDecision.REJECTED, "Ana", "Revisão manual")));
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any(Transaction.class));
    }

    @Test
    void manualReviewDecisionCannotReplaceAnExistingDecision() {
        UUID id = UUID.randomUUID();
        Transaction transaction = new Transaction(
                id,
                new BigDecimal("1250.00"),
                "Loja teste",
                Instant.parse("2026-11-13T12:00:00Z"));
        transaction.completeAnalysis(new AnalysisCompleted(
                id,
                new BigDecimal("1250.00"),
                "Loja teste",
                "REVIEW",
                "Valor acima do limite",
                Instant.parse("2026-11-13T12:01:00Z")));
        transaction.decideReview(
                new ManualReviewRequest(ManualReviewDecision.REJECTED, "Ana", "Não reconhecida"),
                Instant.parse("2026-11-13T12:02:00Z"));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(transaction));

        assertThrows(
                IllegalStateException.class,
                () -> service.decideReview(
                        id,
                        new ManualReviewRequest(ManualReviewDecision.APPROVED, "Bruno", "Alteração")));
        assertEquals("REJECTED", transaction.getManualDecision());
    }
}
