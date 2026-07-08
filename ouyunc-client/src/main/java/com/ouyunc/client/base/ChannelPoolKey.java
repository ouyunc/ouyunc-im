package com.ouyunc.client.base;

import com.ouyunc.base.model.Protocol;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author fzx
 * @description 客户端channel pool
 */
public class ChannelPoolKey implements Serializable {

    /***
     * 协议
     */
    private Protocol protocol;

    /***
     * 远端服务地址
     */
    private String serverAddress;

    public ChannelPoolKey(Protocol protocol, String serverAddress) {
        this.protocol = protocol;
        this.serverAddress = serverAddress;
    }

    public ChannelPoolKey() {
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChannelPoolKey that)) return false;
        return Objects.equals(protocol.getProtocol(), that.protocol.getProtocol()) && Objects.equals(protocol.getProtocolVersion(), that.protocol.getProtocolVersion())  && Objects.equals(serverAddress, that.serverAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol.getProtocol(), protocol.getProtocolVersion(), serverAddress);
    }
}
