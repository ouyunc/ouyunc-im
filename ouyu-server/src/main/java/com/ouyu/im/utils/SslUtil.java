package com.ouyu.im.utils;

import com.ouyu.im.config.PropertiesConfig;
import com.ouyu.im.context.IMContext;
import com.ouyu.im.exception.IMException;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.function.Consumer;

/**
 * @Author fangzhenxun
 * @Description: ssl 工具类
 * @Version V1.0
 **/
public class SslUtil {
    private static Logger log = LoggerFactory.getLogger(SslUtil.class);

    /**
     * keyCertChainFile an X.509 certificate chain file in PEM format
     */
   private final static File keyCertChainFile;

    /**
     *  a PKCS#8 private key file in PEM format
     */
   private final static File keyFile;

    static {
        keyCertChainFile = new File(SslUtil.class.getClassLoader().getResource(IMContext.SERVER_CONFIG.getSslCertificate()).getFile());
        keyFile = new File(SslUtil.class.getClassLoader().getResource(IMContext.SERVER_CONFIG.getSslPrivateKey()).getFile());
    }

    /**
     * @Author fangzhenxun
     * @Description 构建SslContext, 注意这里是指测试，在实际生产中需要替换成正式的证书所生成的SslContext
     * @param
     * @return io.netty.handler.ssl.SslContext
     */
    public static SslContext buildServerSslContext() {
        try {
            return SslContextBuilder.forServer(keyCertChainFile, keyFile).build();
        } catch (Exception e) {
            e.printStackTrace();
            log.error("构建服务端证书异常：{}", e.getMessage());
        }
        throw new IMException("构建服务端证书异常!");
    }

    /**
     * @Author fangzhenxun
     * @Description 构建内置客户端SslContext
     * @param
     * @return io.netty.handler.ssl.SslContext
     */
    public static SslContext buildClientSslContext() {
        try {
            return SslContextBuilder.forClient().keyManager(keyCertChainFile, keyFile).build();
        } catch (Exception e) {
            e.printStackTrace();
            log.error("构建内置客户端证书异常：{}", e.getMessage());
        }
        throw new IMException("构建内置客户端证书异常!");
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


    public static void main(String[] args) {
        String code = "474554202f20485454502f312e310d0a486f73743a206f7579756e632e636f6d3a363030310d0a436f6e6e656374696f6e3a20557067726164650d0a507261676d613a206e6f2d63616368650d0a43616368652d436f6e74726f6c3a206e6f2d63616368650d0a557365722d4167656e743a204d6f7a696c6c612f352e30202857696e646f7773204e542031302e303b2057696e36343b2078363429204170706c655765624b69742f3533372e333620284b48544d4c2c206c696b65204765636b6f29204368726f6d652f39312e302e343437322e3737205361666172692f3533372e33360d0a557067726164653a20776562736f636b65740d0a4f726967696e3a20687474703a2f2f7777772e776562736f636b65742d746573742e636f6d0d0a5365632d576562536f636b65742d56657273696f6e3a2031330d0a4163636570742d456e636f64696e673a20677a69702c206465666c6174650d0a4163636570742d4c616e67756167653a207a682d434e2c7a683b713d302e390d0a5365632d576562536f636b65742d4b65793a204d6d48773652756a3864726f5a784f784c42677744673d3d0d0a5365632d576562536f636b65742d457874656e73696f6e733a207065726d6573736167652d6465666c6174653b20636c69656e745f6d61785f77696e646f775f626974730d0a0d0a";
        byte[] bytes = ByteBufUtil.decodeHexDump(code);
        System.out.println(new String(bytes));
    }

}
