package cn.zcn.websocket;

import java.util.UUID;

/**
 * @author zicung
 */
public class MessageId {

    public static String create() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
