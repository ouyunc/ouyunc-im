package com.ouyunc.message.validator;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.LoginScopeEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.utils.LoginSignatureUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.domain.entity.AppEntity;
import com.ouyunc.repository.DefaultRepository;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 原生登录与 MQTT CONNECT 共用：scope、建档、签名 {@code MD5(appKey&identity&createTime_appSecret)}。
 */
public final class LoginAuthValidator {

    private static final Logger log = LoggerFactory.getLogger(LoginAuthValidator.class);

    private LoginAuthValidator() {
    }

    public static boolean verify(LoginContent loginContent) {
        if (loginContent == null || !LoginScopeEnum.isDefinedType(loginContent.getScope())) {
            return false;
        }
        if (StringUtils.isAnyBlank(loginContent.getAppKey(), loginContent.getIdentity(), loginContent.getSignature())) {
            return false;
        }
        if (LoginScopeEnum.isCustomerService(loginContent.getScope())
                && !LoginUserValidator.userExists(loginContent.getAppKey(), loginContent.getIdentity())) {
            return false;
        }
        AppEntity app = DefaultRepository.INSTANCE.getAppEntity(loginContent.getAppKey());
        if (app == null || StringUtils.isBlank(app.getAppSecret())) {
            log.warn("登录签名校验失败：appKey={} 不存在或无 appSecret", loginContent.getAppKey());
            return false;
        }
        long createTime = loginContent.getCreateTime();
        if (!LoginSignatureUtil.isCreateTimeValid(createTime, TimeUtil.currentTimeMillis())) {
            log.warn("登录签名校验失败：createTime 无效或过期 appKey={} identity={}",
                    loginContent.getAppKey(), loginContent.getIdentity());
            return false;
        }
        String raw = LoginSignatureUtil.buildRaw(
                loginContent.getAppKey(), loginContent.getIdentity(), createTime, app.getAppSecret());
        Encrypt.AsymmetricEncrypt algo = Encrypt.AsymmetricEncrypt.prototype(loginContent.getSignatureAlgorithm());
        if (algo == null) {
            algo = Encrypt.AsymmetricEncrypt.MD5;
        }
        return algo.validate(raw, loginContent.getSignature());
    }

    /**
     * MQTT CONNECT password：{@code createTime#signature}，与原生登录同一套签名。
     */
    public static MqttPassword parseMqttPassword(String password) {
        if (StringUtils.isBlank(password)) {
            return null;
        }
        int idx = password.indexOf(MessageConstant.MQTT_LOGIN_PASSWORD_TIME_SEPARATOR);
        if (idx <= 0 || idx >= password.length() - 1) {
            return null;
        }
        try {
            long createTime = Long.parseLong(password.substring(0, idx).trim());
            String signature = password.substring(idx + 1).trim();
            if (StringUtils.isBlank(signature)) {
                return null;
            }
            return new MqttPassword(createTime, signature);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record MqttPassword(long createTime, String signature) {
    }
}
