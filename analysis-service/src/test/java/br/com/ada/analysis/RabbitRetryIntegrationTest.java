package br.com.ada.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

@EnabledIfEnvironmentVariable(named = "RUN_RABBIT_INTEGRATION", matches = "true")
@SpringBootTest(
        classes = RabbitRetryIntegrationTest.TestApplication.class,
        properties = {
                "spring.main.web-application-type=none",
                "app.rabbit.queue=retry.integration.queue",
                "app.rabbit.exchange=retry.integration.exchange",
                "app.rabbit.routing-key=retry.integration.received"
        })
class RabbitRetryIntegrationTest {
    private static final String QUEUE = "retry.integration.queue";
    private static final String DLQ = QUEUE + ".dlq";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @MockBean
    private AnalysisService service;

    @BeforeEach
    void resetState() {
        rabbitAdmin.purgeQueue(QUEUE);
        rabbitAdmin.purgeQueue(DLQ);
        reset(service);
    }

    @Test
    void sendsMessageToDeadLetterQueueAfterThreeAttemptsWithBackoff() throws Exception {
        List<Long> attempts = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            attempts.add(System.nanoTime());
            throw new IllegalStateException("Falha simulada");
        }).when(service).process(any(TransactionReceived.class));
        String message = contractMessage();

        rabbitTemplate.convertAndSend("retry.integration.exchange", "retry.integration.received", message);

        assertEquals(message, rabbitTemplate.receiveAndConvert(DLQ, 15000));
        verify(service, times(3)).process(any(TransactionReceived.class));
        assertEquals(3, attempts.size());
        assertTrue(TimeUnit.NANOSECONDS.toMillis(attempts.get(1) - attempts.get(0)) >= 900);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(attempts.get(2) - attempts.get(1)) >= 1800);
    }

    @Test
    void transientFailureCanRecoverWithoutDeadLettering() throws Exception {
        List<Long> attempts = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            attempts.add(System.nanoTime());
            if (attempts.size() < 3) {
                throw new IllegalStateException("Falha temporária simulada");
            }
            return null;
        }).when(service).process(any(TransactionReceived.class));

        rabbitTemplate.convertAndSend(
                "retry.integration.exchange", "retry.integration.received", contractMessage());

        verify(service, timeout(15000).times(3)).process(any(TransactionReceived.class));
        assertNull(rabbitTemplate.receive(DLQ, 1000));
    }

    @Test
    void malformedMessageIsSentToDeadLetterQueue() {
        String invalidMessage = "{invalid-json}";
        rabbitTemplate.convertAndSend(
                "retry.integration.exchange", "retry.integration.received", invalidMessage);

        assertEquals(invalidMessage, rabbitTemplate.receiveAndConvert(DLQ, 15000));
        verifyNoInteractions(service);
    }

    private String contractMessage() throws Exception {
        try (InputStream contract = getClass().getResourceAsStream("/contracts/transaction-received.json")) {
            assertNotNull(contract, "Contrato compartilhado não encontrado");
            return new String(contract.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            CacheAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            KafkaAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
    @EnableRabbit
    @Import({RabbitConfiguration.class, AnalysisConsumer.class})
    static class TestApplication {
    }
}
