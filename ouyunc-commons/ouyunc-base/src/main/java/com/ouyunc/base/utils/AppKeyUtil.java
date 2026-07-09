package com.ouyunc.base.utils;

import com.ouyunc.base.constant.MessageConstant;
import org.apache.commons.lang3.StringUtils;

/** appKey 解析工具。 */
public final class AppKeyUtil {

    private AppKeyUtil() {
    }

    /** 为空时使用 {@link MessageConstant#DEFAULT_APP_KEY}。 */
    public static String defaultIfBlank(String appKey) {
        return StringUtils.isNotBlank(appKey) ? appKey.trim() : MessageConstant.DEFAULT_APP_KEY;
    }
}
