package cn.zcn.websocket.gateway.filter;

import cn.zcn.websocket.gateway.utils.PathUtils;
import cn.zcn.websocket.ServiceEntry;
import cn.zcn.websocket.WebSocketRoutingTable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.WebsocketRoutingFilter;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter.filterRequest;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.*;

/**
 * @author Spencer Gibb
 * @author Nikita Konev
 */
public class SpringCloudWebSocketRoutingFilter implements GatewayFilter {

    /**
     * Sec-Websocket protocol.
     */
    public static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";

    private static final Log log = LogFactory.getLog(WebsocketRoutingFilter.class);

    private final WebSocketClient webSocketClient;

    private final WebSocketService webSocketService;

    private final ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider;

    private final WebSocketRoutingTable routingTable;

    // do not use this headersFilters directly, use getHeadersFilters() instead.
    private volatile List<HttpHeadersFilter> headersFilters;

    public SpringCloudWebSocketRoutingFilter(WebSocketRoutingTable routingTable,
                                             WebSocketClient webSocketClient,
                                             WebSocketService webSocketService,
                                             ObjectProvider<List<HttpHeadersFilter>> headersFiltersProvider) {
        this.routingTable = routingTable;
        this.webSocketClient = webSocketClient;
        this.webSocketService = webSocketService;
        this.headersFiltersProvider = headersFiltersProvider;
    }

    /* for testing */
    static String convertHttpToWs(String scheme) {
        scheme = scheme.toLowerCase();
        return "http".equals(scheme) ? "ws" : "https".equals(scheme) ? "wss" : scheme;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        changeSchemeIfIsWebSocketUpgrade(exchange);

        URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
        String scheme = requestUrl.getScheme();

        if (isAlreadyRouted(exchange) || (!"ws".equals(scheme) && !"wss".equals(scheme))) {
            return chain.filter(exchange);
        }
        setAlreadyRouted(exchange);

        Response<ServiceInstance> response = (Response<ServiceInstance>) exchange.getAttributes()
                .get(ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR);
        String uid = PathUtils.getUidFromQuery(requestUrl.getQuery());
        ServiceInstance serviceInstance = response != null && response.hasServer() ? response.getServer() : null;
        Assert.notNull(uid, "Uid should not be null after WebsocketConsistentHashLoadBalancer.");
        Assert.notNull(uid, "ServiceInstance should not be null after WebsocketConsistentHashLoadBalancer.");

        HttpHeaders headers = exchange.getRequest().getHeaders();
        HttpHeaders filtered = filterRequest(getHeadersFilters(), exchange);
        List<String> protocols = getProtocols(headers);

        return this.webSocketService.handleRequest(exchange,
                new ProxyWebSocketHandler(requestUrl, this.webSocketClient, filtered, protocols,
                        () -> routingTable.register(uid, ServiceEntry.from(serviceInstance)),
                        () -> routingTable.unregister(uid),
                        () -> routingTable.renew(uid)));
    }

    /* for testing */ List<String> getProtocols(HttpHeaders headers) {
        List<String> protocols = headers.get(SEC_WEBSOCKET_PROTOCOL);
        if (protocols != null) {
            ArrayList<String> updatedProtocols = new ArrayList<>();
            for (int i = 0; i < protocols.size(); i++) {
                String protocol = protocols.get(i);
                updatedProtocols.addAll(Arrays.asList(StringUtils.tokenizeToStringArray(protocol, ",")));
            }
            protocols = updatedProtocols;
        }
        return protocols;
    }

    /* for testing */ List<HttpHeadersFilter> getHeadersFilters() {
        if (this.headersFilters == null) {
            this.headersFilters = this.headersFiltersProvider.getIfAvailable(ArrayList::new);

            // remove host header unless specifically asked not to
            headersFilters.add((headers, exchange) -> {
                HttpHeaders filtered = new HttpHeaders();
                filtered.addAll(headers);
                filtered.remove(HttpHeaders.HOST);
                boolean preserveHost = exchange.getAttributeOrDefault(PRESERVE_HOST_HEADER_ATTRIBUTE, false);
                if (preserveHost) {
                    String host = exchange.getRequest().getHeaders().getFirst(HttpHeaders.HOST);
                    filtered.add(HttpHeaders.HOST, host);
                }
                return filtered;
            });

            headersFilters.add((headers, exchange) -> {
                HttpHeaders filtered = new HttpHeaders();
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    if (!entry.getKey().toLowerCase().startsWith("sec-websocket")) {
                        filtered.addAll(entry.getKey(), entry.getValue());
                    }
                }
                return filtered;
            });
        }

        return this.headersFilters;
    }

    static void changeSchemeIfIsWebSocketUpgrade(ServerWebExchange exchange) {
        // Check the Upgrade
        URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);
        String scheme = requestUrl.getScheme().toLowerCase();
        String upgrade = exchange.getRequest().getHeaders().getUpgrade();
        // change the scheme if the socket client send a "http" or "https"
        if ("WebSocket".equalsIgnoreCase(upgrade) && ("http".equals(scheme) || "https".equals(scheme))) {
            String wsScheme = convertHttpToWs(scheme);
            boolean encoded = containsEncodedParts(requestUrl);
            URI wsRequestUrl = UriComponentsBuilder.fromUri(requestUrl).scheme(wsScheme).build(encoded).toUri();
            exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, wsRequestUrl);
            if (log.isTraceEnabled()) {
                log.trace("changeSchemeTo:[" + wsRequestUrl + "]");
            }
        }
    }

    private static class ProxyWebSocketHandler implements WebSocketHandler {

        private final WebSocketClient client;

        private final URI url;

        private final HttpHeaders headers;

        private final List<String> subProtocols;

        private final Supplier<Mono<Boolean>> register;

        private final Supplier<Mono<Boolean>> unregister;

        private final Supplier<Mono<Boolean>> renew;

        private ProxyWebSocketHandler(URI url, WebSocketClient client, HttpHeaders headers, List<String> protocols,
                              Supplier<Mono<Boolean>> register, Supplier<Mono<Boolean>> unregister, Supplier<Mono<Boolean>> renew) {
            this.client = client;
            this.url = url;
            this.headers = headers;
            this.register = register;
            this.unregister = unregister;
            this.renew = renew;
            if (protocols != null) {
                this.subProtocols = protocols;
            } else {
                this.subProtocols = Collections.emptyList();
            }
        }

        @Override
        public List<String> getSubProtocols() {
            return this.subProtocols;
        }

        @Override
        public Mono<Void> handle(WebSocketSession session) {
            // pass headers along so custom headers can be sent through
            return client.execute(url, this.headers, new WebSocketHandler() {

                private CloseStatus adaptCloseStatus(CloseStatus closeStatus) {
                    int code = closeStatus.getCode();
                    if (code > 2999 && code < 5000) {
                        return closeStatus;
                    }
                    switch (code) {
                        case 1000:
                        case 1001:
                        case 1002:
                        case 1003:
                        case 1007:
                        case 1008:
                        case 1009:
                        case 1010:
                        case 1011:
                            return closeStatus;
                        case 1004:
                            // Should not be used in a close frame
                            // RESERVED;
                        case 1005:
                            // Should not be used in a close frame
                            // return CloseStatus.NO_STATUS_CODE;
                        case 1006:
                            // Should not be used in a close frame
                            // return CloseStatus.NO_CLOSE_FRAME;
                        case 1012:
                            // Not in RFC6455
                            // return CloseStatus.SERVICE_RESTARTED;
                        case 1013:
                            // Not in RFC6455
                            // return CloseStatus.SERVICE_OVERLOAD;
                        case 1015:
                            // Should not be used in a close frame
                            // return CloseStatus.TLS_HANDSHAKE_FAILURE;
                        default:
                            return CloseStatus.PROTOCOL_ERROR;
                    }
                }

                @Override
                public Mono<Void> handle(WebSocketSession proxySession) {
                    register.get().onErrorResume(t -> {
                        log.error("Failed to register websocket routing table. Then close websocket.", t);
                        session.close();
                        return Mono.empty();
                    }).subscribe();

                    Mono<Void> serverClose = proxySession.closeStatus().filter(__ -> session.isOpen())
                            .map(this::adaptCloseStatus).flatMap(session::close);

                    Mono<Void> proxyClose = session.closeStatus().filter(__ -> proxySession.isOpen())
                            .map(this::adaptCloseStatus).flatMap(closeStatus -> {
                                unregister.get().subscribe();
                                return proxySession.close();
                            });

                    // Use retain() for Reactor Netty
                    Mono<Void> proxySessionSend = proxySession
                            .send(session.receive().doOnNext(new Consumer<WebSocketMessage>() {
                                @Override
                                public void accept(WebSocketMessage webSocketMessage) {
                                    webSocketMessage.retain();
                                    if (webSocketMessage.getType() == WebSocketMessage.Type.PONG) {
                                        renew.get().subscribe();
                                    }
                                }
                            }));
                    // .log("proxySessionSend", Level.FINE);
                    Mono<Void> serverSessionSend = session
                            .send(proxySession.receive().doOnNext(WebSocketMessage::retain));
                    // .log("sessionSend", Level.FINE);
                    // Ensure closeStatus from one propagates to the other
                    Mono.when(serverClose, proxyClose).subscribe();
                    // Complete when both sessions are done
                    return Mono.zip(proxySessionSend, serverSessionSend).then();
                }

                /**
                 * Copy subProtocols so they are available downstream.
                 * @return available subProtocols.
                 */
                @Override
                public List<String> getSubProtocols() {
                    return ProxyWebSocketHandler.this.subProtocols;
                }
            });
        }
    }
}
