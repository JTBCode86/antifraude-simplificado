package br.com.ada.portal;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TransactionApiClient {
    private final RestClient client;

    public TransactionApiClient(@Qualifier("transactionRestClient") RestClient client) {
        this.client = client;
    }

    public JsonNode findRecent() {
        return client.get().uri("/api/transacoes").retrieve().body(JsonNode.class);
    }

    public JsonNode create(Object request) {
        return client.post().uri("/api/transacoes").body(request).retrieve().body(JsonNode.class);
    }

    public JsonNode findStatus(UUID transactionId) {
        return client.get()
                .uri("/api/transacoes/{transactionId}/status", transactionId)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode decideReview(UUID transactionId, Object request) {
        return client.put()
                .uri("/api/transacoes/{transactionId}/review-decision", transactionId)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
    }
}
