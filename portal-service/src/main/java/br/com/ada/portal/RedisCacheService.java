package br.com.ada.portal;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Service;

@Service
public class RedisCacheService {
    private final StringRedisTemplate redisTemplate;

    public RedisCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RedisCacheStatus inspect() {
        return redisTemplate.execute((RedisCallback<RedisCacheStatus>) connection -> {
            String ping = connection.ping();
            long count = 0;
            ScanOptions options = ScanOptions.scanOptions()
                    .match("analysisResults::*")
                    .count(100)
                    .build();
            try (Cursor<byte[]> keys = connection.scan(options)) {
                while (keys.hasNext()) {
                    keys.next();
                    count++;
                }
            }
            Properties memory = connection.serverCommands().info("memory");
            String usedMemory = memory == null
                    ? "Indisponível"
                    : memory.getProperty("used_memory_human", "Indisponível");
            return new RedisCacheStatus("PONG".equalsIgnoreCase(ping) ? "UP" : "DOWN", count, usedMemory);
        });
    }
}
