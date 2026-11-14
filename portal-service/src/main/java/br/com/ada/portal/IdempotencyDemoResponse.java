package br.com.ada.portal;

import java.util.UUID;

public record IdempotencyDemoResponse(
        UUID transactionId,
        int duplicateMessagesPublished,
        long analysisCount,
        String analysisStatus,
        String message) {
}
