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

    private String manualDecision;

    private String reviewedBy;

    @Column(length = 500)
    private String reviewReason;

    private Instant reviewedAt;

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

    public String getManualDecision() {
        return manualDecision;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void completeAnalysis(AnalysisCompleted event) {
        status = "ANALYZED";
        analysisStatus = event.status();
        analysisReason = event.reason();
        analyzedAt = event.analyzedAt();
    }

    public void decideReview(ManualReviewRequest request, Instant decisionTime) {
        manualDecision = request.decision().name();
        reviewedBy = request.reviewedBy().trim();
        reviewReason = request.reason().trim();
        reviewedAt = decisionTime;
    }
}
