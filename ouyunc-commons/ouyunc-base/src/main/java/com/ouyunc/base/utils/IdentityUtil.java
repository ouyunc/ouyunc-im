package com.ouyunc.base.utils;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.exception.MessageException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 唯一标识相关工具类
 */
public class IdentityUtil {
    private static final Logger log = LoggerFactory.getLogger(IdentityUtil.class);

    /**
     * 生成客户端绑定的组合唯一标识
     * 三个参数都不为空，这里就不进行校验了
     */
    public static String generalComboIdentity(String appKey, String identity, DeviceType deviceType) {
        return generalComboIdentity(appKey, identity, deviceType.getDeviceTypeName());
    }

    /**
     * 生成客户端绑定的组合唯一标识,如果设备名称为空则直接返回identity
     */
    public static String generalComboIdentity(String appKey, String identity, String deviceName) {
        return appKey + MessageConstant.COLON_SPLIT + identity + MessageConstant.COLON_SPLIT + deviceName;
    }
    /**
     * 恢复原始id标识
     */
    public static String revertAppKey(String comboIdentity) {
        if (StringUtils.isBlank(comboIdentity)) {
            log.error("恢复原始绑定标识失败!");
            throw new MessageException("恢复原始绑定标识失败！");
        }
        return comboIdentity.split(MessageConstant.COLON_SPLIT, 3)[0];
    }
    /**
     * 恢复原始id标识
     */
    public static String revertIdentity(String comboIdentity) {
        if (StringUtils.isBlank(comboIdentity)) {
            log.error("恢复原始绑定标识失败!");
            throw new MessageException("恢复原始绑定标识失败！");
        }
        return comboIdentity.split(MessageConstant.COLON_SPLIT, 3)[1];
    }


    /**
     * 恢复原始设备名称
     */
    public static String revertDeviceType(String comboIdentity) {
        if (comboIdentity == null) {
            log.error("恢复原始绑定标识失败!");
            throw new MessageException("恢复原始绑定标识失败！");
        }
        return comboIdentity.split(MessageConstant.COLON_SPLIT, 3)[2];
    }


    /**
     * 比较str1 和str2 的大小，进行从高到底顺序输出 sessionId；格式如 高字符串:低字符串
     */
    public static String sessionId(String str1, String str2) {
        if (str1 != null && str2 != null) {
            return str1.compareTo(str2) >= 0 ? str1 + MessageConstant.COLON_SPLIT + str2 : str2 + MessageConstant.COLON_SPLIT + str1;
        }
        log.error("组合有序的字符串{} , {} 失败！", str1, str2);
        throw new MessageException("组合有序的字符串失败！");
    }

}
