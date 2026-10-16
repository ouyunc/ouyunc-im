package com.ouyunc.base.packet.message.content;

import java.io.Serial;

/**
 * 访客侧接入或转接。事件 {@code cs.session.assigned} / {@code cs.session.transferred}。
 * 接待坐席 id 在父类 {@code agentId}，这里只放访客展示和继续发消息需要的字段。
 */
public class CsVisitorHandoffEventContent extends CsSessionEventContent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 坐席显示名。 */
    private String agentName;
    /** 坐席工号。 */
    private String jobNo;
    /** 进线服务身份，转接后访客继续用它发消息。 */
    private String serviceIdentity;
    /** true 表示当前接待是机器人。 */
    private Boolean robot;
    /** 给访客看的系统提示，不是聊天正文。 */
    private String message;

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = normalize(agentName);
    }

    public String getJobNo() {
        return jobNo;
    }

    public void setJobNo(String jobNo) {
        this.jobNo = normalize(jobNo);
    }

    public String getServiceIdentity() {
        return serviceIdentity;
    }

    public void setServiceIdentity(String serviceIdentity) {
        this.serviceIdentity = normalize(serviceIdentity);
    }

    public Boolean getRobot() {
        return robot;
    }

    public void setRobot(Boolean robot) {
        this.robot = robot;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = normalize(message);
    }
}
