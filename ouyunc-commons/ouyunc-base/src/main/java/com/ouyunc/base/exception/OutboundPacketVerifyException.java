package com.ouyunc.base.exception;

/**
 * 本节点出站构包未通过协议结构校验。只表示这一次发送失败，不代表对端连接不健康。
 */
public class OutboundPacketVerifyException extends MessageException {

    public OutboundPacketVerifyException(String message) {
        super(message);
    }

    public static boolean isLocalVerifyFailure(Throwable cause) {
        Throwable cursor = cause;
        while (cursor != null) {
            if (cursor instanceof OutboundPacketVerifyException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
