package com.ouyunc.im.resolver;

/**
 * 抽象属性解析
 */
public abstract class AbstractPropertyResolver implements PropertyResolver {


    /**
     * 解析参数
     * @param args
     * @return
     */
    public abstract CommandLineArgs resolverArgs(String... args);
}
