package com.ouyunc.base.utils;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * md5工具类
 */
public class MD5Util {



    /**
     * 计算字符串的MD5值
     * @param text 待计算的字符串
     * @return 计算结果，小写字母和数字
     */
    public static String md5(String text) {
        return new String(DigestUtils.md5(text), StandardCharsets.UTF_8);
    }

    /**
     * 计算字符串的MD5值
     * @param file 待计算的字符串
     * @return 计算结果,小写字母和数字
     */
    public static String md5(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return new String(DigestUtils.md5(fis), StandardCharsets.UTF_8);
        }catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 计算字符串的MD5值
     * @param text 待计算的字符串
     * @return 计算结果，十六进制字符串格式
     */
    public static String md5Hex(String text) {
        return DigestUtils.md5Hex(text);
    }

    /**
     * 计算文件的MD5值
     * @param file 待计算的文件
     * @return 计算结果，十六进制字符串格式
     * @throws IOException
     */
    public static String md5Hex(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return DigestUtils.md5Hex(fis);
        }catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
