package com.ouyunc.base.utils;

import com.ouyunc.base.constant.MessageConstant;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * IM 登录签名：{@code HMAC-SHA256(appSecret, appKey&identity&createTime)}。
 * appSecret 只作为密钥，不拼进原文。
 */
public final class LoginSignatureUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private LoginSignatureUtil() {
    }

    /**
     * 待签名原文，不含密钥。
     */
    public static String buildPayload(String appKey, String identity, long createTime) {
        return StringUtils.defaultString(appKey)
                + MessageConstant.LOGIN_SIGNATURE_FIELD_SEPARATOR
                + StringUtils.defaultString(identity)
                + MessageConstant.LOGIN_SIGNATURE_FIELD_SEPARATOR
                + createTime;
    }

    /**
     * 使用 appSecret 对原文做 HMAC-SHA256，返回小写十六进制。
     */
    public static String sign(String appKey, String identity, long createTime, String appSecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(StringUtils.defaultString(appSecret).getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(buildPayload(appKey, identity, createTime).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("登录 HMAC-SHA256 计算失败", e);
        }
    }

    /**
     * 常量时间比较签名。长度不一致直接拒绝，避免把异常摘要当成合法签名。
     */
    public static boolean matches(String appKey, String identity, long createTime, String appSecret, String signature) {
        if (StringUtils.isBlank(signature)) {
            return false;
        }
        byte[] actual = signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
        byte[] expected = sign(appKey, identity, createTime, appSecret).getBytes(StandardCharsets.US_ASCII);
        return actual.length == expected.length && MessageDigest.isEqual(actual, expected);
    }

    /**
     * {@code createTime} 是否落在允许的时钟窗口内（且必须 &gt; 0）。
     */
    public static boolean isCreateTimeValid(long createTime, long nowMs) {
        if (createTime <= 0L) {
            return false;
        }
        return Math.abs(nowMs - createTime) <= MessageConstant.LOGIN_SIGNATURE_CREATE_TIME_SKEW_MS;
    }
}
