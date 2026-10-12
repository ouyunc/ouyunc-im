package com.ouyunc.message.validator;

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
 * 原生登录校验：scope、用户建档、HMAC-SHA256、一次性 createTime。
 * 签名原文为 {@code appKey&identity&createTime}，appSecret 只作 HMAC 密钥。
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
        if (!LoginUserValidator.userExists(loginContent.getAppKey(), loginContent.getIdentity())) {
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
        if (loginContent.getSignatureAlgorithm() != Encrypt.AsymmetricEncrypt.HMAC_SHA256.getValue()) {
            log.warn("登录签名校验失败：只接受 HMAC-SHA256 appKey={} identity={} algorithm={}",
                    loginContent.getAppKey(), loginContent.getIdentity(), loginContent.getSignatureAlgorithm());
            return false;
        }
        if (!LoginSignatureUtil.matches(loginContent.getAppKey(), loginContent.getIdentity(),
                createTime, app.getAppSecret(), loginContent.getSignature())) {
            log.warn("登录签名校验失败：HMAC 不匹配 appKey={} identity={}",
                    loginContent.getAppKey(), loginContent.getIdentity());
            return false;
        }
        return LoginNonceStore.consume(loginContent.getAppKey(), loginContent.getIdentity(), createTime);
    }
}
