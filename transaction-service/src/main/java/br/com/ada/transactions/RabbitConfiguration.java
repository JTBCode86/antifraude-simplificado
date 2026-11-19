package br.com.ada.transactions;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    @Bean
    Queue transactionsQueue(@Value("${app.rabbit.queue}") String queueName) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange("")
                .deadLetterRoutingKey(queueName + ".dlq")
                .build();
    }

    @Bean
    Queue transactionsDeadLetterQueue(@Value("${app.rabbit.queue}") String queueName) {
        return QueueBuilder.durable(queueName + ".dlq").build();
    }

    @Bean
    TopicExchange transactionsExchange(@Value("${app.rabbit.exchange}") String exchangeName) {
        return new TopicExchange(exchangeName);
    }

    @Bean
    Binding transactionsBinding(
            Queue transactionsQueue,
            TopicExchange transactionsExchange,
            @Value("${app.rabbit.routing-key}") String routingKey) {
        return BindingBuilder.bind(transactionsQueue).to(transactionsExchange).with(routingKey);
    }
}
