package cn.zcn.websocket.gateway.configuration;

import cn.zcn.websocket.gateway.filter.CustomWebSocketRoutingGatewayFilterFactory;
import cn.zcn.websocket.RedisWebSocketRoutingTable;
import cn.zcn.websocket.WebSocketRoutingTable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.WebsocketRoutingFilter;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.WebSocketService;

import java.util.List;

/**
 * @author zicung
 */
@Configuration
@LoadBalancerClient(name = WebSocketLbConfiguration.SERVICE_ID, configuration = WebSocketLbConfiguration.class)
public class GatewayConfiguration {

    @Bean
    public GatewayFilterFactory<Object> webSocketRoutingGatewayFilterFactory(
            WebSocketRoutingTable routingTable,
            WebsocketRoutingFilter filter,
            WebSocketClient webSocketClient,
            WebSocketService webSocketService,
            ObjectProvider<List<HttpHeadersFilter>> headersFilters) {
        return new CustomWebSocketRoutingGatewayFilterFactory(filter.getOrder() - 1, routingTable,
                webSocketClient, webSocketService, headersFilters);
    }

    @Bean
    public WebSocketRoutingTable wsRoutingTable(ReactiveRedisTemplate<String, Object> redisTemplate) {
        return new RedisWebSocketRoutingTable(redisTemplate);
    }

    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(ReactiveRedisConnectionFactory connectionFactory) {
        RedisSerializationContext.SerializationPair<String> stringSerializationPair = RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer.UTF_8);
        RedisSerializationContext.SerializationPair<Object> objectSerializationPair = RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json());
        RedisSerializationContext.RedisSerializationContextBuilder<String, Object> builder =
                RedisSerializationContext.newSerializationContext();
        builder.key(stringSerializationPair);
        builder.value(objectSerializationPair);
        builder.hashKey(stringSerializationPair);
        builder.hashValue(objectSerializationPair);
        builder.string(stringSerializationPair);

        return new ReactiveRedisTemplate<>(connectionFactory, builder.build());
    }
}
