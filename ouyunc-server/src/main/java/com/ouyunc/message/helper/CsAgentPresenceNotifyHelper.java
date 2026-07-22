package com.ouyunc.message.helper;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.LoginScopeEnum;
import com.ouyunc.base.model.CsAgentPresenceNotifyPayload;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.utils.AppKeyUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.properties.MessageServerProperties;
import com.ouyunc.repository.DefaultRepository;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客服坐席 IM 通道 OPEN/CLOSE 后通过 MQ 通知 CS（技能池与 presence grace）。
 */
public final class CsAgentPresenceNotifyHelper {

    private static final Logger log = LoggerFactory.getLogger(CsAgentPresenceNotifyHelper.class);

    private CsAgentPresenceNotifyHelper() {
    }

    public static void notifyIfCsAgent(LoginClientInfo loginInfo, String eventType, String reason) {
        if (loginInfo == null || StringUtils.isBlank(loginInfo.getIdentity())) {
            return;
        }
        if (LoginScopeEnum.fromType(loginInfo.getScope()) != LoginScopeEnum.CS_AGENT) {
            return;
        }
        MessageServerProperties props = serverProperties();
        if (props == null || !props.isCsAgentPresenceEnabled()) {
            return;
        }
        String appKey = AppKeyUtil.defaultIfBlank(loginInfo.getAppKey());
        CsAgentPresenceNotifyPayload body = new CsAgentPresenceNotifyPayload(
                appKey,
                loginInfo.getIdentity(),
                loginInfo.getScope(),
                loginInfo.getDeviceType(),
                reason,
                TimeUtil.currentTimeMillis(),
                eventType);
        String topic = MqConstant.MQ_CS_AGENT_PRESENCE_TOPIC;
        String key = CsAgentPresenceNotifyPayload.messageKey(appKey, loginInfo.getIdentity());
        String json = JSON.toJSONString(body);
        DefaultRepository.INSTANCE.publishJsonAsync(
                topic, key, json,
                "CS agent-presence MQ, agentId=" + loginInfo.getIdentity()
                        + ", eventType=" + eventType + ", reason=" + reason);
        if (log.isDebugEnabled()) {
            log.debug("CS agent-presence 已投递 MQ, topic={}, agentId={}, eventType={}, reason={}",
                    topic, loginInfo.getIdentity(), eventType, reason);
        }
    }

    private static MessageServerProperties serverProperties() {
        if (MessageServerContext.messageProperties instanceof MessageServerProperties props) {
            return props;
        }
        return null;
    }
}
