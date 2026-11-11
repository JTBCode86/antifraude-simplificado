package br.com.ada.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AnalysisConsumer {
    private final ObjectMapper objectMapper;
    private final AnalysisService service;

    public AnalysisConsumer(ObjectMapper objectMapper, AnalysisService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    @RabbitListener(queues = "${app.rabbit.queue}")
    public void receive(String message) throws Exception {
        TransactionReceived event = objectMapper.readValue(message, TransactionReceived.class);
        service.process(event);
    }
}
