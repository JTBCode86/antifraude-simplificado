package br.com.ada.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_results")
public class AnalysisResult {
    @Id
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String merchant;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private Instant analyzedAt;

    protected AnalysisResult() {
    }

    public AnalysisResult(
            UUID transactionId,
            BigDecimal amount,
            String merchant,
            String status,
            String reason,
            Instant analyzedAt) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.merchant = merchant;
        this.status = status;
        this.reason = reason;
        this.analyzedAt = analyzedAt;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMerchant() {
        return merchant;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }
}
