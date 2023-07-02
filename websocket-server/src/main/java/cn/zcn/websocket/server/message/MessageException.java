package cn.zcn.websocket.server.message;

/**
 * @author zicung
 */
public class MessageException extends RuntimeException {
    public MessageException(String msg) {
        super(msg);
    }

    public MessageException(String msg, Throwable e) {
        super(msg, e);
    }
}
