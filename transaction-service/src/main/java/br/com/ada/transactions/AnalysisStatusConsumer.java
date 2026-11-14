package br.com.ada.transactions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AnalysisStatusConsumer {
    private final ObjectMapper objectMapper;
    private final TransactionService service;

    public AnalysisStatusConsumer(ObjectMapper objectMapper, TransactionService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @KafkaListener(
            topics = "${app.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void receive(String message) throws JsonProcessingException {
        AnalysisCompleted event = objectMapper.readValue(message, AnalysisCompleted.class);
        service.completeAnalysis(event);
    }
}
