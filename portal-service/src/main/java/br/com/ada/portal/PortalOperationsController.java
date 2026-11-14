package br.com.ada.portal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/portal")
public class PortalOperationsController {
    private final PortalHealthService healthService;
    private final KafkaEventService kafkaEventService;
    private final RedisCacheService redisCacheService;
    private final IdempotencyDemoService idempotencyDemoService;

    public PortalOperationsController(
            PortalHealthService healthService,
            KafkaEventService kafkaEventService,
            RedisCacheService redisCacheService,
            IdempotencyDemoService idempotencyDemoService) {
        this.healthService = healthService;
        this.kafkaEventService = kafkaEventService;
        this.redisCacheService = redisCacheService;
        this.idempotencyDemoService = idempotencyDemoService;
    }

    @GetMapping("/health")
    public PortalHealthResponse health() {
        return healthService.check();
    }

    @GetMapping("/kafka/events")
    public java.util.List<KafkaEventResponse> recentKafkaEvents(
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return kafkaEventService.findRecent(limit);
    }

    @GetMapping("/redis")
    public RedisCacheStatus redis() {
        return redisCacheService.inspect();
    }

    @PostMapping("/demo/idempotency")
    public IdempotencyDemoResponse demonstrateIdempotency(
            @Valid @RequestBody TransactionProxyController.TransactionRequest request) {
        return idempotencyDemoService.run(request);
    }
}
