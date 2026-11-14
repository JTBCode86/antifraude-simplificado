package br.com.ada.transactions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AnalysisCompleted(
        UUID transactionId,
        BigDecimal amount,
        String merchant,
        String status,
        String reason,
        Instant analyzedAt) {
}
