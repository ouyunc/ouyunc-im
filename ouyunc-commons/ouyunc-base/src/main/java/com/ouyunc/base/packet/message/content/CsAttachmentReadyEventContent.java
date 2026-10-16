package com.ouyunc.base.packet.message.content;

import java.io.Serial;

/**
 * 附件审核通过。事件 {@code cs.attachment.ready}。
 */
public class CsAttachmentReadyEventContent extends CsSessionEventContent {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 附件审核单 id。 */
    private String auditId;

    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = normalize(auditId);
    }
}