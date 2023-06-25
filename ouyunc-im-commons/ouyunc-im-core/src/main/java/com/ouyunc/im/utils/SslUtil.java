package com.ouyunc.im.utils;

import com.ouyunc.im.context.IMContext;
import com.ouyunc.im.exception.IMException;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * @Author fangzhenxun
 * @Description: ssl 工具类
 **/
public class SslUtil {
    private static Logger log = LoggerFactory.getLogger(SslUtil.class);



    /**
     * @Author fangzhenxun
     * @Description 构建SslContext, 注意这里是指测试，在实际生产中需要替换成正式的证书所生成的SslContext
     * @param
     * @return io.netty.handler.ssl.SslContext
     */
    public static SslContext buildServerSslContext() {
        try {
            return SslContextBuilder.forServer(SslUtil.class.getClassLoader().getResourceAsStream(IMContext.IM_CONFIG.getSslCertificate()), SslUtil.class.getClassLoader().getResourceAsStream(IMContext.IM_CONFIG.getSslPrivateKey())).build();
        } catch (Exception e) {
            log.error("构建服务端证书异常：{}", e.getMessage());
            e.printStackTrace();
            throw new IMException("构建服务端证书异常!");
        }
    }

    /**
     * @Author fangzhenxun
     * @Description 构建内置客户端SslContext
     * @param
     * @return io.netty.handler.ssl.SslContext
     */
    public static SslContext buildClientSslContext() {
        try {
            return SslContextBuilder.forClient().keyManager(SslUtil.class.getClassLoader().getResourceAsStream(IMContext.IM_CONFIG.getSslCertificate()), SslUtil.class.getClassLoader().getResourceAsStream(IMContext.IM_CONFIG.getSslPrivateKey())).build();
        } catch (Exception e) {
            log.error("构建内置客户端证书异常：{}", e.getMessage());
            e.printStackTrace();
            throw new IMException("构建内置客户端证书异常!");
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


    public static void main(String[] args) {
        String code = "474554202f20485454502f312e310d0a486f73743a206f7579756e632e636f6d0d0a582d5265616c5f49503a2033362e342e3139312e32340d0a582d466f727761726465642d466f723a2033362e342e3139312e32343a35343039330d0a557067726164653a20776562736f636b65740d0a436f6e6e656374696f6e3a20757067726164650d0a507261676d613a206e6f2d63616368650d0a43616368652d436f6e74726f6c3a206e6f2d63616368650d0a557365722d4167656e743a204d6f7a696c6c612f352e3020286950686f6e653b20435055206950686f6e65204f532031335f325f33206c696b65204d6163204f53205829204170706c655765624b69742f3630352e312e313520284b48544d4c2c206c696b65204765636b6f292056657273696f6e2f31332e302e33204d6f62696c652f313545313438205361666172692f3630342e310d0a4f726967696e3a2068747470733a2f2f6f7579756e632e636f6d3a383038300d0a5365632d576562536f636b65742d56657273696f6e3a2031330d0a4163636570742d456e636f64696e673a20677a69702c206465666c6174652c2062720d0a4163636570742d4c616e67756167653a207a682d434e2c7a683b713d302e390d0a5365632d576562536f636b65742d4b65793a205a6269335439684e66456667376d34665058326657773d3d0d0a5365632d576562536f636b65742d457874656e73696f6e733a207065726d6573736167652d6465666c6174653b20636c69656e745f6d61785f77696e646f775f626974730d0a0d0a";
        byte[] bytes = ByteBufUtil.decodeHexDump(code);
        System.out.println(new String(bytes));
    }

}
