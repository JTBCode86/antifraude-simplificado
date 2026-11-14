package br.com.ada.transactions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    private UUID id;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String merchant;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false, columnDefinition = "varchar(32) default 'RECEIVED'")
    private String status = "RECEIVED";

    private String analysisStatus;

    private String analysisReason;

    private Instant analyzedAt;

    protected Transaction() {
    }

    public Transaction(UUID id, BigDecimal amount, String merchant, Instant createdAt) {
        this.id = id;
        this.amount = amount;
        this.merchant = merchant;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMerchant() {
        return merchant;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getStatus() {
        return status;
    }

    public String getAnalysisStatus() {
        return analysisStatus;
    }

    public String getAnalysisReason() {
        return analysisReason;
    }

    public Instant getAnalyzedAt() {
        return analyzedAt;
    }

    public void completeAnalysis(AnalysisCompleted event) {
        status = "ANALYZED";
        analysisStatus = event.status();
        analysisReason = event.reason();
        analyzedAt = event.analyzedAt();
    }
}
