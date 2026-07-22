package com.ouyunc.base.utils;

import com.ouyunc.base.constant.MessageConstant;
import org.apache.commons.lang3.StringUtils;

/**
 * IM 登录签名：{@code MD5(appKey&identity&createTime_appSecret)}。
 */
public final class LoginSignatureUtil {

    private LoginSignatureUtil() {
    }

    /**
     * 拼接待摘要原文（与客户端 / 文档约定一致）。
     */
    public static String buildRaw(String appKey, String identity, long createTime, String appSecret) {
        return StringUtils.defaultString(appKey)
                + MessageConstant.LOGIN_SIGNATURE_FIELD_SEPARATOR
                + StringUtils.defaultString(identity)
                + MessageConstant.LOGIN_SIGNATURE_FIELD_SEPARATOR
                + createTime
                + MessageConstant.UNDERLINE
                + StringUtils.defaultString(appSecret);
    }

    /**
     * {@code createTime} 是否在允许的时钟偏差内（且必须 &gt; 0）。
     */
    public static boolean isCreateTimeValid(long createTime, long nowMs) {
        if (createTime <= 0L) {
            return false;
        }
        return Math.abs(nowMs - createTime) <= MessageConstant.LOGIN_SIGNATURE_CREATE_TIME_SKEW_MS;
    }
}
