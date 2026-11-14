package br.com.ada.portal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionReceivedMessage(UUID id, BigDecimal amount, String merchant, Instant createdAt) {
}
