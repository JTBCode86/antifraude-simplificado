package br.com.ada.transactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@ExtendWith(MockitoExtension.class)
class TransactionReceivedContractTest {
    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishedMessageMatchesSharedContract() throws Exception {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        TransactionPublisher publisher = new TransactionPublisher(
                rabbitTemplate, mapper, "transactions.exchange", "transactions.received");
        TransactionReceived event = new TransactionReceived(
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                new BigDecimal("125.50"),
                "Loja teste",
                Instant.parse("2026-11-11T12:00:00Z"));

        publisher.publish(event);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate).convertAndSend(
                eq("transactions.exchange"), eq("transactions.received"), message.capture());
        try (InputStream contract = getClass().getResourceAsStream("/contracts/transaction-received.json")) {
            assertNotNull(contract, "Contrato compartilhado não encontrado");
            assertEquals(mapper.readTree(contract), mapper.readTree(message.getValue()));
        }
    }
}
