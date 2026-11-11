package br.com.ada.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionReceived(
        UUID id,
        BigDecimal amount,
        String merchant,
        Instant createdAt) {
}
