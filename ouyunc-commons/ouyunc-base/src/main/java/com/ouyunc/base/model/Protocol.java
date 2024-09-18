package com.ouyunc.base.model;

/**
 * 协议接口
 */
public interface Protocol {
    /**
     * 获取协议值
     */
    byte getProtocol();

    /**
     *  获取协议版本号
     */
    byte getProtocolVersion();
}
