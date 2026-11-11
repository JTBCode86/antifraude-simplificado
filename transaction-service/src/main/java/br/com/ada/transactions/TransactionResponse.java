package br.com.ada.transactions;

import java.util.UUID;

public record TransactionResponse(UUID id, String status) {
}
