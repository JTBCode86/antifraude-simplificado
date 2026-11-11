package br.com.ada.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisService {
    private static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("1000.00");

    private final AnalysisRepository repository;

    public AnalysisService(AnalysisRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @CacheEvict(cacheNames = "analysisResults", key = "#event.id")
    public void process(TransactionReceived event) {
        boolean requiresReview = event.amount().compareTo(REVIEW_THRESHOLD) > 0;
        String status = requiresReview ? "REVIEW" : "APPROVED";
        String reason = requiresReview
                ? "Valor acima do limite didático configurado"
                : "Valor dentro do limite didático configurado";

        repository.insertIfAbsent(
                event.id(),
                event.amount(),
                event.merchant(),
                status,
                reason,
                Instant.now());
    }

    @Cacheable(cacheNames = "analysisResults", key = "#transactionId", unless = "#result == null")
    public AnalysisResponse findByTransactionId(UUID transactionId) {
        return repository.findById(transactionId)
                .map(result -> new AnalysisResponse(
                        result.getTransactionId(),
                        result.getAmount(),
                        result.getMerchant(),
                        result.getStatus(),
                        result.getReason(),
                        result.getAnalyzedAt()))
                .orElse(null);
    }
}
