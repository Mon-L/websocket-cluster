package cn.zcn.websocket;

import org.springframework.cloud.client.ServiceInstance;

import java.net.URI;

/**
 * @author zicung
 */
public class ServiceEntry {

    private String host;
    private int port;
    private boolean secure;
    private URI uri;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceEntry)) return false;

        ServiceEntry entry = (ServiceEntry) o;

        if (port != entry.port) return false;
        if (secure != entry.secure) return false;
        return host.equals(entry.host);
    }

    @Override
    public int hashCode() {
        int result = host.hashCode();
        result = 31 * result + port;
        result = 31 * result + (secure ? 1 : 0);
        return result;
    }

    public static ServiceEntry from(ServiceInstance instance) {
        ServiceEntry entry = new ServiceEntry();
        entry.host = instance.getHost();
        entry.port = instance.getPort();
        entry.secure = instance.isSecure();
        entry.uri = instance.getUri();
        return entry;
    }
}
