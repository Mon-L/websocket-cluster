package cn.zcn.websocket;

import reactor.core.publisher.Mono;

/**
 * @author zicung
 */
public interface WebSocketRoutingTable {

    Mono<ServiceEntry> get(String key);

    Mono<Boolean> register(String key, ServiceEntry service);

    Mono<Boolean> renew(String key);

    Mono<Boolean> unregister(String key);
}
