package com.ouyu.im.processor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ouyu.im.constant.CacheConstant;
import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.constant.enums.MessageEnum;
import com.ouyu.im.context.IMServerContext;
import com.ouyu.im.encrypt.Encrypt;
import com.ouyu.im.entity.LoginUserInfo;
import com.ouyu.im.handler.HeartBeatHandler;
import com.ouyu.im.helper.UserHelper;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.Message;
import com.ouyu.im.packet.message.content.LoginContent;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 登录消息处理
 * @Version V1.0
 **/
public class LoginMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(LoginMessageProcessor.class);


    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_LOGIN;
    }

    /**
     * @Author fangzhenxun
     * @Description 校验非法参数
     * @param loginContent
     * @return boolean
     */
    public boolean validate(LoginContent loginContent) {
        if (StrUtil.isEmpty(loginContent.getIdentity()) || StrUtil.isEmpty(loginContent.getAppKey()) || StrUtil.isEmpty(loginContent.getSignature())  || loginContent.getCreateTime() == 0 || Encrypt.AsymmetricEncrypt.prototype(loginContent.getSignatureAlgorithm()) == null) {
            return false;
        }
        return true;
    }


    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        final Message loginMessage = (Message) packet.getMessage();
        //将消息内容转成message
        LoginContent loginContent = JSONUtil.toBean(loginMessage.getContent(), LoginContent.class);

        final String identity = loginContent.getIdentity();
        //如果之前已经登录（重复登录请求），这里判断是否已经登录过
        //1,从分布式缓存取出该用户
        LoginUserInfo loginUserInfo = IMServerContext.LOGIN_USER_INFO_CACHE.opsForValue().get(CacheConstant.USER_COMMON_CACHE_PREFIX + CacheConstant.LOGIN_CACHE_PREFIX + identity);

        //2,从本地连接中取出该用户的channel
        final ChannelHandlerContext bindCtx = IMServerContext.LOCAL_USER_CHANNEL_CACHE.get(identity);
        // 注意：这里不做那么细的判断
        if(bindCtx == null && loginUserInfo == null) {
            //1,进行参数合法校验，校验失败，结束 ；2,进行签名的校验，校验失败，结束
            // 根据appKey 获取appSecret 然后拼接
            // @todo 注意这里先写死 appKey 获取appSecret
            String rawStr = loginContent.getAppKey() + ImConstant.AND + loginContent.getIdentity() + ImConstant.AND + loginContent.getCreateTime() + ImConstant.UNDERLINE + "ouyunc";
            if (!validate(loginContent) || !Encrypt.AsymmetricEncrypt.prototype(loginContent.getSignatureAlgorithm()).validate(rawStr, loginContent.getSignature())) {
                // 这个关闭会在writeAndFlush结束后执行，很重要
                ctx.close();
            }else {
                // @todo 注意：其实可以直接在这里调用登录的逻辑不需要往下面传递了，这里我继续往下面传递各尽职责
                ctx.fireChannelRead(packet);
            }
        }
    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        final Message loginMessage = (Message) packet.getMessage();
        LoginContent loginContent = JSONUtil.toBean(loginMessage.getContent(), LoginContent.class);

        String identity = loginContent.getIdentity();
        // @todo 这里注意：需要清空之前的该用户的在其他集群服务上的绑定信息（如果存在）
        UserHelper.unbind(identity);
        UserHelper.bind(identity, ctx);
        // 登录完成后进行处理添加心跳信息，由于心跳消息不需要登录就可以，所以放在登录认证处理器前面
        ctx.pipeline()
                .addAfter(new DefaultEventExecutorGroup(16), ImConstant.LOG, ImConstant.HEART_BEAT_IDLE, new IdleStateHandler(IMServerContext.SERVER_CONFIG.getHeartBeatTimeout(),0,0))
                // 处理心跳的以及相关逻辑都放在这里处理
                .addAfter(ImConstant.CONVERT_2_PACKET, ImConstant.HEART_BEAT_HANDLER, new HeartBeatHandler());
    }

    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("login");
        System.out.println("login");

    }
}
