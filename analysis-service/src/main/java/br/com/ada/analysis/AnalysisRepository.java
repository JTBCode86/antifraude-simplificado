package br.com.ada.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisRepository extends JpaRepository<AnalysisResult, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO analysis_results
                (transaction_id, amount, merchant, status, reason, analyzed_at)
            VALUES
                (:transactionId, :amount, :merchant, :status, :reason, :analyzedAt)
            ON CONFLICT (transaction_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("transactionId") UUID transactionId,
            @Param("amount") BigDecimal amount,
            @Param("merchant") String merchant,
            @Param("status") String status,
            @Param("reason") String reason,
            @Param("analyzedAt") Instant analyzedAt);
}
