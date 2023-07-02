package cn.zcn.websocket.gateway.utils;

/**
 * @author zicung
 */
public class PathUtils {
    private static final String USER_ID_KEY = "uid";

    public static String getUidFromQuery(String query) {
        String[] pairs = query.split("&");
        String uid = null;
        for (String pair : pairs) {
            String[] keyAndValue = pair.split("=");
            if (USER_ID_KEY.equals(keyAndValue[0])) {
                uid = keyAndValue[1];
                break;
            }
        }

        return uid;
    }
}
