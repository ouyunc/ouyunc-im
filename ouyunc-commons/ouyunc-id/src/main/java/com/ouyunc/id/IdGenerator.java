package com.ouyunc.id;

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
     * id， 如果不足19位则填充0
     */
    String generateId19Str();

    /**
     * 将long str id 格式化转换成字符串， 如果不足19位则填充0
     */
    String formatLongId19Str(String id);


    /**
     * 将long str id 格式化转换成字符串， 如果不足19位则填充0
     */
    String formatLongId19Str(long id);

    /**
     * 将str id 转换成long
     */
    long formatStrIdAsLong(String id);

}
