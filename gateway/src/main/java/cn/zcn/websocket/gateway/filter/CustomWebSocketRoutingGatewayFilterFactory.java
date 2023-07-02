package cn.zcn.websocket.gateway.filter;

import cn.zcn.websocket.WebSocketRoutingTable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.WebSocketService;

import java.util.List;

/**
 * @author zicung
 */
public class CustomWebSocketRoutingGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private final int order;
    private final WebSocketClient webSocketClient;
    private final WebSocketService webSocketService;
    private final WebSocketRoutingTable routingTable;
    private final ObjectProvider<List<HttpHeadersFilter>> headersFilters;

    public CustomWebSocketRoutingGatewayFilterFactory(int order,
                                                      WebSocketRoutingTable routingTable,
                                                      WebSocketClient webSocketClient,
                                                      WebSocketService webSocketService,
                                                      ObjectProvider<List<HttpHeadersFilter>> headersFilters) {
        this.order = order;
        this.routingTable = routingTable;
        this.webSocketClient = webSocketClient;
        this.webSocketService = webSocketService;
        this.headersFilters = headersFilters;
    }

    @Override
    public GatewayFilter apply(Object config) {
        return new OrderedGatewayFilter(new SpringCloudWebSocketRoutingFilter(
                routingTable, webSocketClient,
                webSocketService, headersFilters),
                order);
    }
}
