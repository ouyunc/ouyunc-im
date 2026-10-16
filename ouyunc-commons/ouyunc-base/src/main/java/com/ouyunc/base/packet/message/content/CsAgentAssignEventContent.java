package com.ouyunc.base.packet.message.content;

import java.io.Serial;

/**
 * 坐席工作台分配快照。事件 {@code cs.session.assigned}，接收方是坐席。
 * 只带列表 upsert 需要的咨询单字段，不带访客侧的工号和提示文案。
 */
public class CsAgentAssignEventContent extends CsSessionEventContent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 访客用户 id，对应咨询单 userId。 */
    private String customerId;
    /** 访客显示名。 */
    private String customerName;
    /** 技能队列编码，取值等于咨询单 queueId。 */
    private String skillCode;
    /** 进线渠道实例编码。 */
    private String channel;
    /** 渠道协议：whatsapp / telegram / line / im。与 {@link #channel} 成对。 */
    private String channelType;
    /** 进线服务身份。 */
    private String serviceIdentity;
    /** 访客偏好语种。 */
    private String customerLocale;
    /** 咨询单服务状态。 */
    private Integer serviceState;
    /** 托管模式。 */
    private Integer hostingMode;

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = normalize(customerId);
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = normalize(customerName);
    }

    public String getSkillCode() {
        return skillCode;
    }

    public void setSkillCode(String skillCode) {
        this.skillCode = normalize(skillCode);
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = normalize(channel);
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = normalize(channelType);
    }

    public String getServiceIdentity() {
        return serviceIdentity;
    }

    public void setServiceIdentity(String serviceIdentity) {
        this.serviceIdentity = normalize(serviceIdentity);
    }

    public String getCustomerLocale() {
        return customerLocale;
    }

    public void setCustomerLocale(String customerLocale) {
        this.customerLocale = normalize(customerLocale);
    }

    public Integer getServiceState() {
        return serviceState;
    }

    public void setServiceState(Integer serviceState) {
        this.serviceState = serviceState;
    }

    public Integer getHostingMode() {
        return hostingMode;
    }

    public void setHostingMode(Integer hostingMode) {
        this.hostingMode = hostingMode;
    }
}
