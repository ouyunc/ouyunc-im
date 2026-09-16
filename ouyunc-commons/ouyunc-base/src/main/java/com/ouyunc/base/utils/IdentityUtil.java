package com.ouyunc.base.utils;


import com.ouyunc.base.constant.MessageConstant;
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
     * 生成客户端绑定的组合唯一标识,如果设备名称为空则直接返回identity
     */
    public static String generalComboIdentity(String appKey, String identity, Byte deviceValue) {
        return appKey + MessageConstant.COLON + identity + MessageConstant.COLON + deviceValue;
    }
    /**
     * combo 为 {@code appKey:identity:deviceType}。identity 可含冒号，只能按首/末冒号切，禁止 split limit=3。
     */
    public static String revertAppKey(String comboIdentity) {
        return splitCombo(comboIdentity)[0];
    }

    /**
     * 恢复原始 identity（中间段，允许含冒号）。
     */
    public static String revertIdentity(String comboIdentity) {
        return splitCombo(comboIdentity)[1];
    }

    /**
     * 恢复设备类型（末段，必须是 Byte）。
     */
    public static Byte revertDeviceType(String comboIdentity) {
        try {
            return Byte.valueOf(splitCombo(comboIdentity)[2]);
        } catch (NumberFormatException e) {
            log.error("恢复原始绑定设备类型失败! combo={}", comboIdentity);
            throw new MessageException("恢复原始绑定标识失败！");
        }
    }

    /**
     * [0]=appKey [1]=identity [2]=deviceType。首冒号切 appKey，末冒号切 deviceType。
     */
    private static String[] splitCombo(String comboIdentity) {
        if (StringUtils.isBlank(comboIdentity)) {
            log.error("恢复原始绑定标识失败!");
            throw new MessageException("恢复原始绑定标识失败！");
        }
        String colon = MessageConstant.COLON;
        int first = comboIdentity.indexOf(colon);
        int last = comboIdentity.lastIndexOf(colon);
        if (first < 0 || last <= first) {
            log.error("恢复原始绑定标识失败! combo={}", comboIdentity);
            throw new MessageException("恢复原始绑定标识失败！");
        }
        String appKey = comboIdentity.substring(0, first);
        String identity = comboIdentity.substring(first + colon.length(), last);
        String deviceType = comboIdentity.substring(last + colon.length());
        if (appKey.isEmpty() || identity.isEmpty() || deviceType.isEmpty()) {
            log.error("恢复原始绑定标识失败! combo={}", comboIdentity);
            throw new MessageException("恢复原始绑定标识失败！");
        }
        return new String[]{appKey, identity, deviceType};
    }


    /**
     * 比较str1 和str2 的大小，进行从高到底顺序输出 sessionId；格式如 高字符串:低字符串
     */
    public static String sessionId(String str1, String str2) {
        if (str1 != null && str2 != null) {
            return str1.compareTo(str2) >= 0 ? str1 + MessageConstant.COLON + str2 : str2 + MessageConstant.COLON + str1;
        }
        log.error("组合有序的字符串{} , {} 失败！", str1, str2);
        throw new MessageException("组合有序的字符串失败！");
    }

}
