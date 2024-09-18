package com.ouyunc.base.model;


import com.ouyunc.base.constant.enums.SendStatusEnum;
import com.ouyunc.base.packet.Packet;

import java.io.Serializable;

/**
 * @Author fzx
 * @Description: 消息发送结果
 **/
public class SendResult implements Serializable {

    /**
     * 发送状态
     */
    private SendStatusEnum sendStatus;

    /**
     * 发送的消息包
     */
    private Packet packet;


    /**
     * 发送异常信息
     */
    private Throwable exception;

    public SendResult() {
    }

    public SendResult(SendStatusEnum sendStatus, Packet packet, Throwable exception) {
        this.sendStatus = sendStatus;
        this.packet = packet;
        this.exception = exception;
    }

    public SendStatusEnum getSendStatus() {
        return sendStatus;
    }

    public void setSendStatus(SendStatusEnum sendStatus) {
        this.sendStatus = sendStatus;
    }

    public Packet getPacket() {
        return packet;
    }

    public void setPacket(Packet packet) {
        this.packet = packet;
    }

    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        /**
         * 发送状态
         */
        private SendStatusEnum sendStatus;

        /**
         * 发送的消息包
         */
        private Packet packet;


        /**
         * 发送异常信息
         */
        private Throwable exception;

        public Builder sendStatus(SendStatusEnum sendStatus) {
            this.sendStatus = sendStatus;
            return this;
        }

        public Builder packet(Packet packet) {
            this.packet = packet;
            return this;
        }

        public Builder exception(Throwable exception) {
            this.exception = exception;
            return this;
        }

        public SendResult build() {
            return new SendResult(this.sendStatus, this.packet, this.exception);
        }
    }
}