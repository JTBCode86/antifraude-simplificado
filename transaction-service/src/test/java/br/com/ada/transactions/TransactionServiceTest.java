package br.com.ada.transactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
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
}
