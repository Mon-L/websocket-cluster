package cn.zcn.websocket.gateway.configuration;

import cn.zcn.websocket.gateway.loadbalancer.WebsocketConsistentHashLoadBalancer;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;

/**
 * @author zicung
 */
public class WebSocketLbConfiguration {

    public static final String SERVICE_ID = "ws";

    @Bean
    public ReactorLoadBalancer<ServiceInstance> wsConsistentHashLoadBalancer(
            LoadBalancerClientFactory loadBalancerClientFactory) {
        return new WebsocketConsistentHashLoadBalancer(SERVICE_ID, loadBalancerClientFactory
                .getLazyProvider(SERVICE_ID, ServiceInstanceListSupplier.class));
    }
}
