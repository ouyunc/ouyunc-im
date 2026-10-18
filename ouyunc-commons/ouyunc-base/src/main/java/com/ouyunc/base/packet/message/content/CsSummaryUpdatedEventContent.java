package com.ouyunc.base.packet.message.content;

import java.io.Serial;

/** 咨询摘要状态变更：转接摘要生成完成/失败，工作台刷新。 */
public class CsSummaryUpdatedEventContent extends CsSessionEventContent {

    @Serial
    private static final long serialVersionUID = 1L;

    private String summaryKind;
    private Integer occurrence;
    private String status;
    private String source;

    public String getSummaryKind() {
        return summaryKind;
    }

    public void setSummaryKind(String summaryKind) {
        this.summaryKind = normalize(summaryKind);
    }

    public Integer getOccurrence() {
        return occurrence;
    }

    public void setOccurrence(Integer occurrence) {
        this.occurrence = occurrence;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = normalize(status);
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = normalize(source);
    }
}
