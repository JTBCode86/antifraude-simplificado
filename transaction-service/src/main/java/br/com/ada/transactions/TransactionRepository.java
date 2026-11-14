package br.com.ada.transactions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findTop100ByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transaction from Transaction transaction where transaction.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") UUID id);
}
