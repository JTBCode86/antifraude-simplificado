package br.com.ada.portal;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api")
public class TransactionProxyController {
    private final TransactionApiClient transactionClient;
    private final AnalysisApiClient analysisClient;

    public TransactionProxyController(
            TransactionApiClient transactionClient,
            AnalysisApiClient analysisClient) {
        this.transactionClient = transactionClient;
        this.analysisClient = analysisClient;
    }

    @GetMapping("/transacoes")
    public JsonNode findRecent() {
        return transactionClient.findRecent();
    }

    @PostMapping("/transacoes")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JsonNode create(@Valid @RequestBody TransactionRequest request) {
        return transactionClient.create(request);
    }

    @GetMapping("/transacoes/{transactionId}/status")
    public JsonNode findStatus(@PathVariable UUID transactionId) {
        return transactionClient.findStatus(transactionId);
    }

    @PutMapping("/transacoes/{transactionId}/review-decision")
    public JsonNode decideReview(
            @PathVariable UUID transactionId,
            @Valid @RequestBody ReviewDecisionRequest request) {
        return transactionClient.decideReview(transactionId, request);
    }

    @GetMapping("/analises/{transactionId}")
    public JsonNode findAnalysis(@PathVariable UUID transactionId) {
        return analysisClient.findByTransactionId(transactionId);
    }

    public record TransactionRequest(
            @NotNull @Positive BigDecimal valor,
            @NotBlank @Size(max = 160) String estabelecimento) {
    }

    public record ReviewDecisionRequest(
            @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision,
            @NotBlank @Size(max = 80) String reviewedBy,
            @NotBlank @Size(max = 500) String reason) {
    }
}
