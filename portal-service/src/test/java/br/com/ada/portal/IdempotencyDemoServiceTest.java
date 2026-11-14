package br.com.ada.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class IdempotencyDemoServiceTest {
    @Mock
    private TransactionApiClient transactionClient;

    @Mock
    private AnalysisApiClient analysisClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void runPublishesDuplicateEventsAndConfirmsOnePersistedAnalysis() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        UUID transactionId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-14T12:00:00Z");
        when(transactionClient.create(any())).thenReturn(objectMapper.readTree(
                "{\"id\":\"" + transactionId + "\",\"status\":\"RECEIVED\"}"));
        when(transactionClient.findStatus(transactionId)).thenReturn(objectMapper.readTree(
                "{\"amount\":1250.00,\"merchant\":\"Loja teste\",\"createdAt\":\"" + createdAt + "\"}"));
        when(analysisClient.countByTransactionId(transactionId)).thenReturn(
                objectMapper.readTree("{\"count\":1}"));
        when(analysisClient.findByTransactionId(transactionId)).thenReturn(
                objectMapper.readTree("{\"status\":\"REVIEW\"}"));

        IdempotencyDemoService service = new IdempotencyDemoService(
                transactionClient,
                analysisClient,
                rabbitTemplate,
                objectMapper,
                "transactions.exchange",
                "transactions.received");

        IdempotencyDemoResponse response = service.run(
                new TransactionProxyController.TransactionRequest(
                        new BigDecimal("1250.00"),
                        "Loja teste"));

        assertEquals(transactionId, response.transactionId());
        assertEquals(2, response.duplicateMessagesPublished());
        assertEquals(1, response.analysisCount());
        assertEquals("REVIEW", response.analysisStatus());
        verify(rabbitTemplate, times(2))
                .convertAndSend(eq("transactions.exchange"), eq("transactions.received"), anyString());
    }
}
