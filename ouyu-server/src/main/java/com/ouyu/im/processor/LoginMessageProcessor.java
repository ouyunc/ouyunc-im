package com.ouyu.im.processor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.context.IMContext;
import com.ouyu.im.encrypt.Encrypt;
import com.ouyu.im.entity.ChannelUserInfo;
import com.ouyu.im.entity.LoginUserInfo;
import com.ouyu.im.handler.HeartBeatHandler;
import com.ouyu.im.helper.UserHelper;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.LoginMessage;
import com.ouyu.im.packet.message.ResponseMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
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




    /**
     * @Author fangzhenxun
     * @Description 校验非法参数
     * @param loginMessage
     * @return boolean
     */
    public boolean validate(LoginMessage loginMessage) {
        if (StrUtil.isEmpty(loginMessage.getIdentity()) || StrUtil.isEmpty(loginMessage.getAppKey()) || StrUtil.isEmpty(loginMessage.getSignature())  || loginMessage.getCreateTime() == 0 || Encrypt.AsymmetricEncrypt.prototype(loginMessage.getSignatureAlgorithm()) == null) {
            return false;
        }
        return true;
    }


    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        final LoginMessage loginMessage = (LoginMessage) packet.getMessage();
        final String identity = loginMessage.getIdentity();
        //如果之前已经登录（重复登录请求），这里判断是否已经登录过
        //1,从分布式缓存取出该用户
        // @todo 这里需要优化处理从缓存取数据的问题
        Object obj = IMContext.LOGIN_USER_INFO_CACHE.get(identity);
        LoginUserInfo loginUserInfo = null;
        if (obj != null) {
            loginUserInfo = JSONUtil.toBean(JSONUtil.toJsonStr(obj), LoginUserInfo.class);
        }
        //2,从本地连接中取出该用户的channel
        final ChannelHandlerContext bindCtx = IMContext.LOCAL_USER_CHANNEL_CACHE.get(identity);

        if (bindCtx != null && loginUserInfo != null) {
            // 如果都不为空，接着继续判断
            //3,从channel中的attrMap取出相关属性
            AttributeKey<ChannelUserInfo> channelTagLoginKey = AttributeKey.valueOf(ImConstant.CHANNEL_TAG_LOGIN);
            final ChannelUserInfo authenticationUserInfo = bindCtx.channel().attr(channelTagLoginKey).get();
            //4,判断是否已经登录，如果已经登录则提示已经登录，不做处理，如果未登录则走下面的登录逻辑
            if (authenticationUserInfo != null && ctx.channel().id().asLongText().equals(bindCtx.channel().id().asLongText())) {
                //5,说明已经登录过
                packet.setMessage(ResponseMessage.success("已经登录，请不要重复登录！"));
                ctx.writeAndFlush(packet);
            }else{
                //6,该通道没有绑定任何信息
                packet.setMessage(ResponseMessage.fail("该通道信息绑定异常！现将关闭该通道"));
                ctx.writeAndFlush(packet);
                //有异常,移除并关闭,游离channel
                IMContext.LOGIN_USER_INFO_CACHE.delete(identity);
                IMContext.LOCAL_USER_CHANNEL_CACHE.invalidate(identity);
                // 关闭channel
                ctx.close();
            }
        }else if(bindCtx == null && loginUserInfo == null){
            // 没有登录，需要走登录的逻辑
            AttributeKey<ChannelUserInfo> channelTagLoginKey = AttributeKey.valueOf(ImConstant.CHANNEL_TAG_LOGIN);
            ChannelUserInfo authenticationUserInfo = ctx.channel().attr(channelTagLoginKey).get();
            if (authenticationUserInfo == null) {
                //1,进行参数合法校验，校验失败，结束 ；2,进行签名的校验，校验失败，结束
                // 根据appKey 获取appSecret 然后拼接
                String rawStr = loginMessage.getAppKey() + ImConstant.AND + loginMessage.getIdentity() + ImConstant.AND + loginMessage.getCreateTime() + ImConstant.UNDERLINE + "123456";
                if (!validate(loginMessage) || !Encrypt.AsymmetricEncrypt.prototype(loginMessage.getSignatureAlgorithm()).validate(rawStr, loginMessage.getSignature())) {
                    packet.setMessage(ResponseMessage.fail("登录校验失败！"));
                    ctx.writeAndFlush(packet);
                    // 这个关闭会在writeAndFlush结束后执行，很重要
                    ctx.close();
                }else {
                    // @todo 注意：其实可以直接在这里调用登录的逻辑不需要往下面传递了，这里我继续往下面传递各尽职责
                    ctx.fireChannelRead(packet);
                }
            }else {
                // 取出 唯一标识，反向校验该channel是否被绑定
                final String identity0 = authenticationUserInfo.getIdentity();
                LoginUserInfo loginUserInfo0 = (LoginUserInfo) IMContext.LOGIN_USER_INFO_CACHE.get(identity0);
                //2,从本地连接中取出该用户的channel
                final ChannelHandlerContext bindCtx0 = IMContext.LOCAL_USER_CHANNEL_CACHE.get(identity0);
                if (loginUserInfo0 == null || bindCtx0 == null) {
                    //有异常,移除并关闭,游离channel
                    IMContext.LOGIN_USER_INFO_CACHE.delete(identity);
                    IMContext.LOCAL_USER_CHANNEL_CACHE.invalidate(identity);
                    ctx.close();
                }

            }
        } else {
            //有异常,移除并关闭
            IMContext.LOGIN_USER_INFO_CACHE.delete(identity);
            IMContext.LOCAL_USER_CHANNEL_CACHE.invalidate(identity);
            ctx.close();
        }
    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        final LoginMessage loginMessage = (LoginMessage) packet.getMessage();
        String identity = loginMessage.getIdentity();
        // @todo 这里注意：需要清空之前的该用户的在其他集群服务上的绑定信息（如果存在）
        UserHelper.unbind(identity);
        UserHelper.bind(identity, ctx);
        // 登录完成后进行处理添加心跳信息
        ctx.pipeline()
                .addAfter(new DefaultEventExecutorGroup(16), ImConstant.LOG, ImConstant.HEART_BEAT_IDLE, new IdleStateHandler(loginMessage.getHeartBeatReadTimeout(),0,0))
                // 处理心跳的以及相关逻辑都放在这里处理
                .addAfter(ImConstant.CONVERT_2_PACKET, ImConstant.HEART_BEAT_HANDLER, new HeartBeatHandler());
    }

    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("login");
        System.out.println("login");

    }
}
