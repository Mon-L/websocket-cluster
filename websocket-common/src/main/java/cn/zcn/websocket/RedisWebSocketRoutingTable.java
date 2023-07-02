package cn.zcn.websocket;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * @author zicung
 */
public class RedisWebSocketRoutingTable implements WebSocketRoutingTable {

    private static final String KEY = "gateway:ws:uid:{id}";

    private static final int TIMEOUT_MILLIS = 1000 * 60 * 30;

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public RedisWebSocketRoutingTable(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<ServiceEntry> get(String key) {
        return redisTemplate.opsForValue().get(getKey(key)).ofType(ServiceEntry.class);
    }

    @Override
    public Mono<Boolean> register(String key, ServiceEntry service) {
        return redisTemplate.opsForValue().set(getKey(key), service, Duration.ofMillis(TIMEOUT_MILLIS));
    }

    @Override
    public Mono<Boolean> renew(String key) {
        return redisTemplate.expire(getKey(key), Duration.ofMillis(TIMEOUT_MILLIS));
    }

    @Override
    public Mono<Boolean> unregister(String key) {
        return redisTemplate.opsForValue().delete(getKey(key));
    }

    private String getKey(String uid) {
        return KEY.replace("{id}", uid);
    }
}
