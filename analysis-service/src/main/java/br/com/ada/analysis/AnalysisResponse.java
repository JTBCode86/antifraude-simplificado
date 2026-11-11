package br.com.ada.analysis;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AnalysisResponse(
        UUID transactionId,
        BigDecimal amount,
        String merchant,
        String status,
        String reason,
        Instant analyzedAt) implements Serializable {
}
