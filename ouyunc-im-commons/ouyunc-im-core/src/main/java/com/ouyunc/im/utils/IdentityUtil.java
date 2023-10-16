package com.ouyunc.im.utils;

import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.DeviceEnum;
import com.ouyunc.im.exception.IMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 唯一标识相关工具类
 */
public class IdentityUtil {
    private static Logger log = LoggerFactory.getLogger(IdentityUtil.class);


    /**
     * 生成客户端绑定的组合唯一标识
     * @param identity 原始登录唯一标识
     * @param deviceType   设备类型
     * @return
     */
    public static String generalComboIdentity(String identity, byte deviceType) {
        return identity + IMConstant.COLON_SPLIT + DeviceEnum.getDeviceNameByValue(deviceType);
    }

    /**
     * 生成客户端绑定的组合唯一标识
     * @param identity 原始登录唯一标识
     * @param deviceEnum   设备类型枚举
     * @return
     */
    public static String generalComboIdentity(String identity, DeviceEnum deviceEnum) {
        return identity + IMConstant.COLON_SPLIT + deviceEnum.getName();
    }

    /**
     * 生成客户端绑定的组合唯一标识
     * @param identity 原始登录唯一标识
     * @param deviceName   设备名称
     * @return
     */
    public static String generalComboIdentity(String identity, String deviceName) {
        return identity + IMConstant.COLON_SPLIT + deviceName;
    }

    /**
     * 恢复原始id标识
     * @param comboIdentity
     * @return
     */
    public static String revertIdentity(String comboIdentity) {
        if (comboIdentity == null) {
            log.error("恢复原始绑定标识失败!");
            return null;
        }
        return comboIdentity.split(IMConstant.COLON_SPLIT)[0];
    }

    /**
     * 恢复原始设备名称
     * @param comboIdentity
     * @return
     */
    public static String revertDevice(String comboIdentity) {
        if (comboIdentity == null) {
            log.error("恢复原始绑定标识失败!");
            return null;
        }
        return comboIdentity.split(IMConstant.COLON_SPLIT)[1];
    }

    /**
     * 返回所支持的在线登录设备，目前支持两个，m-移动端(包括H5)，pc-电脑端
     * @return
     */
    public static List<String> supportOnlineLoginDevice() {
        List<String> supportOnlineLoginDeviceList = new ArrayList<>();
        supportOnlineLoginDeviceList.add(DeviceEnum.M_OTHER.getName());
        supportOnlineLoginDeviceList.add(DeviceEnum.PC_OTHER.getName());
        return supportOnlineLoginDeviceList;
    }


    /**
     * 比较str1 和str2 的大小，进行从高到底顺序输出；格式如 高字符串:低字符串
     * @param str1
     * @param str2
     * @return
     */
    public static String sortComboIdentity(String str1, String str2) {
        if (str1 != null && str2 != null) {
            return str1.compareTo(str2) >= 0?  str1 + IMConstant.COLON_SPLIT + str2 : str2 + IMConstant.COLON_SPLIT + str1;
        }
        log.error("组合有序的字符串{} , {} 失败！", str1, str2);
        throw new IMException("组合有序的字符串失败！");
    }

}
