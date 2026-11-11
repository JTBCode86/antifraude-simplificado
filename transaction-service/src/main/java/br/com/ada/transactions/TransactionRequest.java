package br.com.ada.transactions;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record TransactionRequest(
        @NotNull @Positive BigDecimal valor,
        @NotBlank String estabelecimento) {
}
