package br.com.ada.transactions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionStatusResponse(
        UUID id,
        BigDecimal amount,
        String merchant,
        String status,
        String analysisStatus,
        String analysisReason,
        Instant createdAt,
        Instant analyzedAt) {
}
