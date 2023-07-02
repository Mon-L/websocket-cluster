package cn.zcn.websocket.gateway.loadbalancer;

import cn.zcn.websocket.gateway.utils.Md5Util;
import cn.zcn.websocket.gateway.utils.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.SelectedInstanceCallback;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author zicung
 */
public class WebsocketConsistentHashLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(WebsocketConsistentHashLoadBalancer.class);

    private final String serviceId;
    private final ConcurrentMap<Integer, ConsistentHashSelector> consistentHashSelectors = new ConcurrentHashMap<>();
    private final ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider;

    public WebsocketConsistentHashLoadBalancer(String serviceId,
                                               ObjectProvider<ServiceInstanceListSupplier> serviceInstanceListSupplierProvider) {
        this.serviceId = serviceId;
        this.serviceInstanceListSupplierProvider = serviceInstanceListSupplierProvider;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        DefaultRequestContext requestContext = (DefaultRequestContext) request.getContext();
        RequestData clientRequest = (RequestData) requestContext.getClientRequest();
        String query = clientRequest.getUrl().getQuery();
        if (query == null) {
            if (log.isWarnEnabled()) {
                log.warn("No query to choose service.");
            }
            return Mono.just(new EmptyResponse());
        }

        String uid = PathUtils.getUidFromQuery(query);
        if (uid == null) {
            if (log.isWarnEnabled()) {
                log.warn("No uid to choose service: " + query);
            }
            return Mono.just(new EmptyResponse());
        }

        ServiceInstanceListSupplier supplier = this.serviceInstanceListSupplierProvider
                .getIfAvailable(NoopServiceInstanceListSupplier::new);

        return supplier.get(request)
                .next()
                .map((serviceInstances) -> this.processInstanceResponse(supplier, uid, serviceInstances, clientRequest));
    }

    private Response<ServiceInstance> processInstanceResponse(ServiceInstanceListSupplier supplier, String uid,
                                                              List<ServiceInstance> serviceInstances, RequestData request) {
        Response<ServiceInstance> serviceInstanceResponse = this.getInstanceResponse(uid, serviceInstances, request);
        if (supplier instanceof SelectedInstanceCallback && serviceInstanceResponse.hasServer()) {
            ((SelectedInstanceCallback) supplier).selectedServiceInstance(serviceInstanceResponse.getServer());
        }

        return serviceInstanceResponse;
    }

    private Response<ServiceInstance> getInstanceResponse(String uid, List<ServiceInstance> instances, RequestData request) {
        if (instances.isEmpty()) {
            if (log.isWarnEnabled()) {
                log.warn("No servers available for service: " + this.serviceId);
            }

            return new EmptyResponse();
        } else if (instances.size() == 1) {
            return new DefaultResponse(instances.get(0));
        }

        int hashcode = instances.hashCode();
        ConsistentHashSelector selector = consistentHashSelectors.get(hashcode);
        if (selector == null || selector.hashcode != hashcode) {
            ConsistentHashSelector newSelector = new ConsistentHashSelector(hashcode, instances);
            selector = consistentHashSelectors.putIfAbsent(hashcode, newSelector);
            if (selector == null) {
                selector = newSelector;
            }
        }

        return new DefaultResponse(selector.select(uid));
    }

    private static class ConsistentHashSelector {

        private static final int DEFAULT_REPLICA_NUM = 160;
        private final int hashcode;
        private final TreeMap<Long, ServiceInstance> nodes = new TreeMap<>();

        private ConsistentHashSelector(int hashcode, List<ServiceInstance> instances) {
            this.hashcode = hashcode;

            for (ServiceInstance provider : instances) {
                for (int i = 0; i < DEFAULT_REPLICA_NUM / 4; i++) {
                    for (long position : getKetamaNodePositions(provider.getUri().toString() + "-" + i)) {
                        nodes.put(position, provider);
                    }
                }
            }
        }

        private ServiceInstance select(String uid) {
            byte[] digest = Md5Util.computeMd5(uid);
            long hash = getKetamaHash(digest, 0);

            Map.Entry<Long, ServiceInstance> entry = nodes.ceilingEntry(hash);
            if (entry == null) {
                entry = nodes.firstEntry();
            }

            return entry.getValue();
        }

        private List<Long> getKetamaNodePositions(String key) {
            List<Long> positions = new ArrayList<>();
            byte[] digest = Md5Util.computeMd5(key);

            for (int h = 0; h < 4; h++) {
                positions.add(getKetamaHash(digest, h));
            }

            return positions;
        }

        private long getKetamaHash(byte[] bytes, int i) {
            //@formatter:off
            long hash = ((long) (bytes[3 + i * 4] & 0xFF) << 24)
                    | ((long) (bytes[2 + i * 4] & 0xFF) << 16)
                    | ((long) (bytes[1 + i * 4] & 0xFF) << 8)
                    | (bytes[0] & 0xFF);
            //@formatter:on

            /* Truncate to 32-bits */
            return hash & 0xffffffffL;
        }
    }
}
