package br.com.ada.portal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.KafkaException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class PortalHealthService {
    private final RestClient transactionManagementClient;
    private final RestClient analysisManagementClient;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    private final String kafkaBootstrapServers;

    public PortalHealthService(
            @Qualifier("transactionManagementClient") RestClient transactionManagementClient,
            @Qualifier("analysisManagementClient") RestClient analysisManagementClient,
            RabbitTemplate rabbitTemplate,
            StringRedisTemplate redisTemplate,
            @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrapServers) {
        this.transactionManagementClient = transactionManagementClient;
        this.analysisManagementClient = analysisManagementClient;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }

    public PortalHealthResponse check() {
        List<HealthComponent> components = new ArrayList<>();
        components.add(new HealthComponent("Portal web", "UP", "Portal BFF operando"));
        addServiceHealth(components, "Serviço de Transações", transactionManagementClient);
        addServiceHealth(components, "Serviço de Análise", analysisManagementClient);
        addRabbitHealth(components);
        addRedisHealth(components);
        addKafkaHealth(components);
        String overall = components.stream().allMatch(component -> "UP".equals(component.status()))
                ? "UP"
                : "DOWN";
        return new PortalHealthResponse(overall, Instant.now(), List.copyOf(components));
    }

    private void addServiceHealth(List<HealthComponent> components, String serviceName, RestClient client) {
        try {
            Map<String, Object> health = client.get()
                    .uri("/actuator/health")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            String status = String.valueOf(health.getOrDefault("status", "UNKNOWN"));
            components.add(new HealthComponent(serviceName, status, "Estado geral"));
            Object rawComponents = health.get("components");
            if (rawComponents instanceof Map<?, ?> serviceComponents) {
                serviceComponents.forEach((name, value) -> {
                    String componentStatus = "UNKNOWN";
                    if (value instanceof Map<?, ?> details) {
                        Object rawStatus = details.get("status");
                        componentStatus = rawStatus == null ? "UNKNOWN" : String.valueOf(rawStatus);
                    }
                    components.add(new HealthComponent(
                            serviceName + " · " + name,
                            componentStatus,
                            "Dependência verificada pelo Actuator"));
                });
            }
        } catch (RestClientException exception) {
            components.add(new HealthComponent(serviceName, "DOWN", "Endpoint de saúde indisponível"));
        }
    }

    private void addRabbitHealth(List<HealthComponent> components) {
        try {
            Boolean open = rabbitTemplate.execute(channel -> channel.isOpen());
            components.add(new HealthComponent(
                    "RabbitMQ",
                    Boolean.TRUE.equals(open) ? "UP" : "DOWN",
                    "Conexão AMQP do portal"));
        } catch (AmqpException exception) {
            components.add(new HealthComponent("RabbitMQ", "DOWN", "Conexão AMQP indisponível"));
        }
    }

    private void addRedisHealth(List<HealthComponent> components) {
        try {
            String pong = redisTemplate.execute(RedisConnection::ping);
            components.add(new HealthComponent(
                    "Redis",
                    "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN",
                    "Conexão do cache"));
        } catch (DataAccessException exception) {
            components.add(new HealthComponent("Redis", "DOWN", "Conexão com o cache indisponível"));
        }
    }

    private void addKafkaHealth(List<HealthComponent> components) {
        Map<String, Object> configuration = new HashMap<>();
        configuration.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        try (AdminClient admin = AdminClient.create(configuration)) {
            int brokerCount = admin.describeCluster().nodes().get(3, TimeUnit.SECONDS).size();
            components.add(new HealthComponent(
                    "Kafka",
                    brokerCount > 0 ? "UP" : "DOWN",
                    brokerCount + " broker(s) disponíveis"));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            components.add(new HealthComponent("Kafka", "DOWN", "Verificação interrompida"));
        } catch (ExecutionException
                | TimeoutException
                | KafkaException
                | org.apache.kafka.common.KafkaException exception) {
            components.add(new HealthComponent("Kafka", "DOWN", "Broker indisponível"));
        }
    }
}
