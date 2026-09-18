package com.ouyunc.base.utils;

import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @Author fzx
 * @Description: ssl/tls 工具类
 **/
public class SSLUtil {
    private static final Logger log = LoggerFactory.getLogger(SSLUtil.class);

    private static final ConcurrentHashMap<String, SslContext> SERVER_CONTEXT_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, SslContext> CLIENT_CONTEXT_CACHE = new ConcurrentHashMap<>();

    /**
     * @Author fangzhenxun
     * @Description 构建SslContext, 注意这里是指测试，在实际生产中需要替换成正式的证书所生成的SslContext
     * @param
     * @return io.netty.handler.ssl.SslContext
     */
    public static SslContext buildServerSslContext(String sslCertificate, String sslPrivateKey) {
        String cacheKey = cacheKey("server", sslCertificate, sslPrivateKey);
        return SERVER_CONTEXT_CACHE.computeIfAbsent(cacheKey, ignored -> {
            try (InputStream cert = openClasspath(sslCertificate);
                 InputStream key = openClasspath(sslPrivateKey)) {
                return SslContextBuilder.forServer(cert, key).build();
            } catch (Exception e) {
                log.error("构建服务端证书异常：{}", e.getMessage());
                throw new RuntimeException("构建服务端证书异常!");
            }
        });
    }

    /**
     * @Author fangzhenxun
     * @Description 构建内置客户端SslContext
     * @param
     * @return io.netty.handler.ssl.SslContext
     */
    public static SslContext buildClientSslContext(String sslCertificate, String sslPrivateKey) {
        String cacheKey = cacheKey("client", sslCertificate, sslPrivateKey);
        return CLIENT_CONTEXT_CACHE.computeIfAbsent(cacheKey, ignored -> {
            try (InputStream cert = openClasspath(sslCertificate);
                 InputStream key = openClasspath(sslPrivateKey)) {
                return SslContextBuilder.forClient().keyManager(cert, key).build();
            } catch (Exception e) {
                log.error("构建内置客户端证书异常：{}", e.getMessage());
                throw new RuntimeException("构建内置客户端证书异常!");
            }
        });
    }

    private static String cacheKey(String role, String certificate, String privateKey) {
        return role + "|" + String.valueOf(certificate) + "|" + String.valueOf(privateKey);
    }

    private static InputStream openClasspath(String path) {
        InputStream stream = SSLUtil.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new RuntimeException("SSL 证书资源不存在: " + path);
        }
        return stream;
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
