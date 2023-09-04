package com.ouyunc.im.log;

import com.ouyunc.im.packet.Packet;

/**
 * 定义日志接口，记录每个消息包的完整传递链路
 */
public interface Log {

    /**
     * 记录日志
     * @param packet
     */
    void log(Packet packet);

    /**
     * 清除日志
     */
    void clear();
}
