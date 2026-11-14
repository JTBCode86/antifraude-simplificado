package br.com.ada.transactions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {
    private final TransactionRepository repository;
    private final TransactionPublisher publisher;

    public TransactionService(TransactionRepository repository, TransactionPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        Transaction transaction = new Transaction(id, request.valor(), request.estabelecimento(), createdAt);
        repository.save(transaction);

        publisher.publish(new TransactionReceived(
                id,
                transaction.getAmount(),
                transaction.getMerchant(),
                transaction.getCreatedAt()));
        return new TransactionResponse(id, "RECEIVED");
    }

    @Transactional
    public void completeAnalysis(AnalysisCompleted event) {
        Transaction transaction = repository.findById(event.transactionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Analysis received for unknown transaction " + event.transactionId()));
        transaction.completeAnalysis(event);
        repository.save(transaction);
    }

    @Transactional(readOnly = true)
    public Optional<TransactionStatusResponse> findStatus(UUID transactionId) {
        return repository.findById(transactionId)
                .map(this::toStatusResponse);
    }

    @Transactional(readOnly = true)
    public List<TransactionStatusResponse> findRecent() {
        return repository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(this::toStatusResponse)
                .toList();
    }

    private TransactionStatusResponse toStatusResponse(Transaction transaction) {
        return new TransactionStatusResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getMerchant(),
                transaction.getStatus(),
                transaction.getAnalysisStatus(),
                transaction.getAnalysisReason(),
                transaction.getCreatedAt(),
                transaction.getAnalyzedAt());
    }
}
