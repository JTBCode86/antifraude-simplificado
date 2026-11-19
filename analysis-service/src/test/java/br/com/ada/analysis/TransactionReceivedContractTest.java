package br.com.ada.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@ExtendWith(MockitoExtension.class)
class TransactionReceivedContractTest {
    @Mock
    private AnalysisService service;

    @Test
    void consumerReadsAndProcessesSharedContract() throws Exception {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        AnalysisConsumer consumer = new AnalysisConsumer(mapper, service);
        try (InputStream contract = getClass().getResourceAsStream("/contracts/transaction-received.json")) {
            assertNotNull(contract, "Contrato compartilhado não encontrado");
            consumer.receive(new String(contract.readAllBytes(), StandardCharsets.UTF_8));
        }

        ArgumentCaptor<TransactionReceived> event = ArgumentCaptor.forClass(TransactionReceived.class);
        verify(service).process(event.capture());
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), event.getValue().id());
        assertEquals(new BigDecimal("125.50"), event.getValue().amount());
        assertEquals("Loja teste", event.getValue().merchant());
        assertEquals(Instant.parse("2026-11-11T12:00:00Z"), event.getValue().createdAt());
    }
}
