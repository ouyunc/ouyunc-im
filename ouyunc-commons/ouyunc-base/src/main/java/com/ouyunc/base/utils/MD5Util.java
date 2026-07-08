package com.ouyunc.base.utils;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * md5工具类
 */
public class MD5Util {


    /**
     * 计算字符串的MD5值
     * @param text 待计算的字符串
     * @return 计算结果，十六进制字符串格式
     */
    public static String md5(String text) {
        return DigestUtils.md5Hex(text);
    }

    /**
     * 计算文件的MD5值
     * @param file 待计算的文件
     * @return 计算结果，十六进制字符串格式
     * @throws IOException
     */
    public static String md5(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return DigestUtils.md5Hex(fis);
        }
    }
}
