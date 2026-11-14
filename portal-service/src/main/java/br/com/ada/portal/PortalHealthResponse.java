package br.com.ada.portal;

import java.time.Instant;
import java.util.List;

public record PortalHealthResponse(String status, Instant checkedAt, List<HealthComponent> components) {
}
