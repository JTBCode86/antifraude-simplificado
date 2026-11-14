package br.com.ada.portal;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KafkaEventService {
    private final String bootstrapServers;
    private final String topic;

    public KafkaEventService(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${app.kafka.topic}") String topic) {
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
    }

    public List<KafkaEventResponse> findRecent(int limit) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "portal-event-viewer-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            var partitionsInfo = consumer.partitionsFor(topic, Duration.ofSeconds(3));
            if (partitionsInfo == null || partitionsInfo.isEmpty()) {
                return List.of();
            }
            List<TopicPartition> partitions = partitionsInfo.stream()
                    .map(info -> new TopicPartition(topic, info.partition()))
                    .toList();
            consumer.assign(partitions);
            Map<TopicPartition, Long> beginnings = consumer.beginningOffsets(partitions, Duration.ofSeconds(3));
            Map<TopicPartition, Long> endings = consumer.endOffsets(partitions, Duration.ofSeconds(3));
            for (TopicPartition partition : partitions) {
                long end = endings.get(partition);
                long start = Math.max(beginnings.get(partition), end - limit);
                consumer.seek(partition, start);
            }

            List<KafkaEventResponse> events = new ArrayList<>();
            Instant deadline = Instant.now().plusSeconds(3);
            while (Instant.now().isBefore(deadline) && hasUnreadOffsets(consumer, partitions, endings)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    events.add(new KafkaEventResponse(
                            record.key(),
                            record.value(),
                            record.partition(),
                            record.offset(),
                            Instant.ofEpochMilli(record.timestamp())));
                }
            }
            return events.stream()
                    .sorted(Comparator.comparing(KafkaEventResponse::timestamp).reversed())
                    .limit(limit)
                    .toList();
        }
    }

    private boolean hasUnreadOffsets(
            KafkaConsumer<String, String> consumer,
            List<TopicPartition> partitions,
            Map<TopicPartition, Long> endings) {
        return partitions.stream().anyMatch(partition ->
                consumer.position(partition) < endings.get(partition));
    }
}
