package cn.zcn.websocket.server.controller;

import cn.zcn.websocket.Message;
import cn.zcn.websocket.SingleMessage;
import cn.zcn.websocket.server.message.MessageService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;

/**
 * @author zicung
 */
@Controller
public class MessageController {

    @Resource
    private MessageService messageService;

    @MessageMapping("/sendToUser")
    public void sendToUser(SingleMessage message) {
        messageService.sendToUser(message);
    }

    @RequestMapping(value = "/dispatchToUser", method = RequestMethod.POST)
    public void dispatchToUser(SingleMessage message) {
        messageService.sendToUser(message);
    }

    @RequestMapping(value = "/broadcast", method = RequestMethod.POST)
    public void broadcast(Message message) {
        messageService.broadcast(message);
    }
}