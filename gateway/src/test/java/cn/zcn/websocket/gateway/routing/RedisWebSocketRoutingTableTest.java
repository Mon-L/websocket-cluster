package cn.zcn.websocket.gateway.routing;

import cn.zcn.websocket.ServiceEntry;
import cn.zcn.websocket.WebSocketRoutingTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author zicung
 */
@SpringBootTest
public class RedisWebSocketRoutingTableTest {

    @Autowired
    private WebSocketRoutingTable routingTable;

    @Test
    public void testRegister() {
        ServiceEntry service = new ServiceEntry();
        service.setHost("127.0.0.1");
        service.setPort(8888);
        service.setSecure(false);
        Assertions.assertEquals(Boolean.TRUE, routingTable.register("123", service).block());
        Assertions.assertEquals(service, routingTable.get("123").block());
    }

    @Test
    public void testRenew() {
        Assertions.assertEquals(Boolean.TRUE, routingTable.renew("123").block());
    }

    @Test
    public void testUnregister() {
        Assertions.assertEquals(Boolean.TRUE, routingTable.unregister("123").block());
    }
}
