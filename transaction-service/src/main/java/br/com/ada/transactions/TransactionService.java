package br.com.ada.transactions;

import java.time.Instant;
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
}
