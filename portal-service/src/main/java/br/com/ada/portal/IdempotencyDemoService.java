package br.com.ada.portal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdempotencyDemoService {
    private final TransactionApiClient transactionClient;
    private final AnalysisApiClient analysisClient;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final String routingKey;

    public IdempotencyDemoService(
            TransactionApiClient transactionClient,
            AnalysisApiClient analysisClient,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${app.rabbit.exchange}") String exchange,
            @Value("${app.rabbit.routing-key}") String routingKey) {
        this.transactionClient = transactionClient;
        this.analysisClient = analysisClient;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public IdempotencyDemoResponse run(TransactionProxyController.TransactionRequest request) {
        JsonNode created = transactionClient.create(request);
        UUID transactionId = UUID.fromString(created.path("id").asText());
        JsonNode status = transactionClient.findStatus(transactionId);
        TransactionReceivedMessage event = new TransactionReceivedMessage(
                transactionId,
                status.path("amount").decimalValue(),
                status.path("merchant").asText(),
                Instant.parse(status.path("createdAt").asText()));
        try {
            String payload = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(exchange, routingKey, payload);
            rabbitTemplate.convertAndSend(exchange, routingKey, payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize idempotency demonstration event", exception);
        }

        long count = 0;
        String analysisStatus = null;
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            JsonNode verification = analysisClient.countByTransactionId(transactionId);
            count = verification.path("count").asLong();
            if (count > 0) {
                JsonNode analysis = analysisClient.findByTransactionId(transactionId);
                analysisStatus = analysis.path("status").asText();
                break;
            }
            sleepBeforeRetry();
        }
        if (count != 1) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "The duplicate messages were published, but one analysis was not confirmed in time");
        }
        return new IdempotencyDemoResponse(
                transactionId,
                2,
                count,
                analysisStatus,
                "A transação original e as duas mensagens repetidas resultaram em uma única análise.");
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Idempotency demonstration interrupted", exception);
        }
    }
}
