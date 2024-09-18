package com.ouyunc.base.model;

import java.util.Objects;

/**
 * 协议类型
 */
public class ProtocolType implements Protocol{

    private byte protocol;

    private byte protocolVersion;

    @Override
    public byte getProtocol() {
        return protocol;
    }

    public void setProtocol(byte protocol) {
        this.protocol = protocol;
    }

    @Override
    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(byte protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public ProtocolType(byte protocol, byte protocolVersion) {
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProtocolType that = (ProtocolType) o;
        return protocol == that.protocol && protocolVersion == that.protocolVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol, protocolVersion);
    }
}
