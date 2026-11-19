package br.com.ada.transactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

class RabbitConfigurationTest {
    @Test
    void declaresSameDeadLetterDestinationAsConsumer() {
        RabbitConfiguration configuration = new RabbitConfiguration();
        Queue queue = configuration.transactionsQueue("transactions.queue");
        Queue deadLetterQueue = configuration.transactionsDeadLetterQueue("transactions.queue");

        assertTrue(queue.isDurable());
        assertEquals("", queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("transactions.queue.dlq", queue.getArguments().get("x-dead-letter-routing-key"));
        assertEquals("transactions.queue.dlq", deadLetterQueue.getName());
        assertTrue(deadLetterQueue.isDurable());
    }
}
