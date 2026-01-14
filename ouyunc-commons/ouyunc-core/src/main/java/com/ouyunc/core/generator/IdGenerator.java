package com.ouyunc.core.generator;

/**
 * 全局唯一 id生成器
 */
public interface IdGenerator {


    /**
     * id生成器接口
     */
    long generateId();

    /**
     * id生成器接口
     */
    String generateIdStr();

    /**
     * 将long id 格式化转换成字符串， 如果不足19位则填充0
     */
    String formatLong(long id);

    /**
     * 将long str id 格式化转换成字符串， 如果不足19位则填充0
     */
    String formatLong(String id);

}
