package com.ouyunc.base.utils;

import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * @Author fzx
 * @Description: ssl/tls 工具类
 **/
public class SSLUtil {
    private static final Logger log = LoggerFactory.getLogger(SSLUtil.class);



    /**
     * @Author fangzhenxun
     * @Description 构建SslContext, 注意这里是指测试，在实际生产中需要替换成正式的证书所生成的SslContext
     * @param
     * @return io.netty.handler.ssl.SslContext
     */
    public static SslContext buildServerSslContext(String sslCertificate, String sslPrivateKey) {
        try {
            return SslContextBuilder.forServer(SSLUtil.class.getClassLoader().getResourceAsStream(sslCertificate), SSLUtil.class.getClassLoader().getResourceAsStream(sslPrivateKey)).build();
        } catch (Exception e) {
            log.error("构建服务端证书异常：{}", e.getMessage());
            throw new RuntimeException("构建服务端证书异常!");
        }
    }

    /**
     * @Author fangzhenxun
     * @Description 构建内置客户端SslContext
     * @param
     * @return io.netty.handler.ssl.SslContext
     */
    public static SslContext buildClientSslContext(String sslCertificate, String sslPrivateKey) {
        try {
            return SslContextBuilder.forClient().keyManager(SSLUtil.class.getClassLoader().getResourceAsStream(sslCertificate), SSLUtil.class.getClassLoader().getResourceAsStream(sslPrivateKey)).build();
        } catch (Exception e) {
            log.error("构建内置客户端证书异常：{}", e.getMessage());
            throw new RuntimeException("构建内置客户端证书异常!");
        }
    }


    /**
     * @Author fangzhenxun
     * @Description 配置SSL
     * @param
     * @return io.netty.handler.ssl.SslContext
     */
    public static void configSSL(Consumer<Channel> consumer, Channel channel) {
        consumer.accept(channel);
    }



}
