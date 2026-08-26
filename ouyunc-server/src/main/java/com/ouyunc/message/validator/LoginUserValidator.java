package com.ouyunc.message.validator;

import com.ouyunc.domain.entity.UserEntity;
import com.ouyunc.repository.DefaultRepository;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 原生 IM 登录：identity 必须是本 appKey 下未删除、未封禁的 {@code ouyunc_im_user}。
 * 签名只能证明知道 secret，不能证明该号已建档。
 */
public final class LoginUserValidator {

    private static final Logger log = LoggerFactory.getLogger(LoginUserValidator.class);

    /** {@code ouyunc_im_user.status}：1-正常 */
    private static final int STATUS_NORMAL = 1;

    private LoginUserValidator() {
    }

    public static boolean userExists(String appKey, String identity) {
        if (StringUtils.isAnyBlank(appKey, identity)) {
            return false;
        }
        try {
            UserEntity user = DefaultRepository.INSTANCE.getUserEntity(appKey, identity);
            if (user == null) {
                log.warn("登录拒绝：通讯账号不存在 appKey={} identity={}", appKey, identity);
                return false;
            }
            if (!StringUtils.equals(appKey, user.getAppKey())) {
                log.warn("登录拒绝：通讯账号不属于该应用 appKey={} identity={}", appKey, identity);
                return false;
            }
            if (user.getStatus() != null && user.getStatus() != STATUS_NORMAL) {
                log.warn("登录拒绝：通讯账号已封禁 identity={}", identity);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("登录校验查询用户失败 identity={}", identity, e);
            return false;
        }
    }
}
