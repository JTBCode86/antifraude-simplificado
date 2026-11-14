package br.com.ada.portal;

import java.time.Instant;

public record KafkaEventResponse(String key, String value, int partition, long offset, Instant timestamp) {
}
