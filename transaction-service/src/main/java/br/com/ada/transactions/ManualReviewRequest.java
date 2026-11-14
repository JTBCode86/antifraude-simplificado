package br.com.ada.transactions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualReviewRequest(
        @NotNull ManualReviewDecision decision,
        @NotBlank @Size(max = 80) String reviewedBy,
        @NotBlank @Size(max = 500) String reason) {
}
