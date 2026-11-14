package br.com.ada.analysis;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AnalysisServiceCacheTest.CacheTestConfiguration.class)
class AnalysisServiceCacheTest {
    private static final String CACHE_NAME = "analysisResults";

    @Autowired
    private AnalysisService service;

    @Autowired
    private AnalysisRepository repository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCacheAndRepository() {
        reset(repository);
        cacheManager.getCache(CACHE_NAME).clear();
    }

    @Test
    void secondReadUsesCacheInsteadOfQueryingRepositoryAgain() {
        UUID transactionId = UUID.randomUUID();
        AnalysisResult stored = analysis(transactionId);
        when(repository.findById(transactionId)).thenReturn(Optional.of(stored));

        assertNotNull(service.findByTransactionId(transactionId));
        assertNotNull(service.findByTransactionId(transactionId));

        verify(repository, times(1)).findById(transactionId);
    }

    @Test
    void missingAnalysisIsNotCached() {
        UUID transactionId = UUID.randomUUID();
        when(repository.findById(transactionId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(analysis(transactionId)));

        assertNull(service.findByTransactionId(transactionId));
        assertNotNull(service.findByTransactionId(transactionId));

        verify(repository, times(2)).findById(transactionId);
    }

    private AnalysisResult analysis(UUID transactionId) {
        return new AnalysisResult(
                transactionId,
                new BigDecimal("125.00"),
                "Loja teste",
                "APPROVED",
                "Valor dentro do limite didático configurado",
                Instant.parse("2026-11-11T12:00:00Z"));
    }

    @Configuration
    @EnableCaching
    static class CacheTestConfiguration {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CACHE_NAME);
        }

        @Bean
        AnalysisRepository analysisRepository() {
            return mock(AnalysisRepository.class);
        }

        @Bean
        AnalysisService analysisService(
                AnalysisRepository repository,
                ApplicationEventPublisher eventPublisher) {
            return new AnalysisService(repository, eventPublisher);
        }
    }
}
