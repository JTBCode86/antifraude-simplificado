package br.com.ada.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class AnalysisEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisEventPublisher.class);

    private final KafkaTemplate<String, AnalysisCompleted> kafkaTemplate;
    private final String topic;

    public AnalysisEventPublisher(
            KafkaTemplate<String, AnalysisCompleted> kafkaTemplate,
            @Value("${app.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(AnalysisCompleted event) {
        kafkaTemplate.send(topic, event.transactionId().toString(), event)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        LOGGER.error(
                                "Failed to publish analysis event for transaction {} to topic {}",
                                event.transactionId(),
                                topic,
                                exception);
                        return;
                    }

                    LOGGER.info(
                            "Published analysis event for transaction {} to topic {} at offset {}",
                            event.transactionId(),
                            topic,
                            result.getRecordMetadata().offset());
                });
    }
}
