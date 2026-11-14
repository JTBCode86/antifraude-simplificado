package br.com.ada.portal;

public record RedisCacheStatus(String status, long cachedAnalyses, String memoryUsed) {
}
