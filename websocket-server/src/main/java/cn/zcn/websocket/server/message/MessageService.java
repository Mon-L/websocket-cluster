package cn.zcn.websocket.server.message;

import cn.zcn.websocket.*;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

/**
 * @author zicung
 */
@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private Registration registration;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    @Resource
    private SimpMessagingTemplate messagingTemplate;

    @Resource
    private WebSocketRoutingTable webSocketRoutingTable;

    public void sendToUser(SingleMessage message) throws MessageException {
        preSend(message);
        ServiceEntry service = webSocketRoutingTable.get(message.getUid()).block();
        if (service == null) {
            log.debug("No service found, receiver disconnect. MsgId:{}, User:{}", message.getId(), message.getUid());
            return;
        }

        if (isLocalJvm(service)) {
            log.debug("Send message to receiver which connected with same service. MsgId:{}, Remoting:{}, User:{}",
                    message.getId(), service.getUri().toString(), message.getUid());
            messagingTemplate.convertAndSendToUser(message.getUid(), "/chat/notification",
                    message.getContent());
        } else {
            log.debug("Dispatch message to remoting service. MsgId:{}, Remoting:{}, User:{}",
                    message.getId(), service.getUri().toString(), message.getUid());

            try {
                restTemplate.postForEntity(service.getUri(), message.getContent(), Void.class);
            } catch (RestClientException e) {
                throw new MessageException("Failed to dispatch message. " + e.getMessage(), e);
            }
        }
    }

    public void broadcast(Message message) throws MessageException {
        preSend(message);
        SendResult sendResult = rocketMQTemplate.syncSend("broadcast", message);
        if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
            throw new MessageException("Failed to send message to MQ. SendStatus:" +
                    sendResult.getSendStatus().name());
        }
    }

    public void onBroadcast(Message message) {
        log.debug("Broadcast message. MsgId:{}", message.getId());
        messagingTemplate.convertAndSend("/chat/notification", message.getContent());
    }

    private void preSend(Message message) {
        message.setId(MessageId.create());
    }

    private boolean isLocalJvm(ServiceEntry serviceEntry) {
        if (serviceEntry == null) {
            return false;
        }

        return serviceEntry.getHost().equals(registration.getHost()) &&
                serviceEntry.getPort() == registration.getPort();
    }
}
