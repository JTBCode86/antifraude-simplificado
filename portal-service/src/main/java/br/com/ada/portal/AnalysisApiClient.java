package br.com.ada.portal;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AnalysisApiClient {
    private final RestClient client;

    public AnalysisApiClient(@Qualifier("analysisRestClient") RestClient client) {
        this.client = client;
    }

    public JsonNode findByTransactionId(UUID transactionId) {
        return client.get()
                .uri("/api/analises/{transactionId}", transactionId)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode countByTransactionId(UUID transactionId) {
        return client.get()
                .uri("/api/analises/{transactionId}/idempotency-count", transactionId)
                .retrieve()
                .body(JsonNode.class);
    }

}
