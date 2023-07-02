package cn.zcn.websocket.server.message;

import cn.zcn.websocket.Message;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author zicung
 */
@Component
@RocketMQMessageListener(consumerGroup = "ws-server", topic = "broadcast", messageModel = MessageModel.BROADCASTING)
public class BroadcastMessageListener implements RocketMQListener<Message> {

    @Resource
    private MessageService messageService;

    @Override
    public void onMessage(Message message) {
        messageService.onBroadcast(message);
    }
}
