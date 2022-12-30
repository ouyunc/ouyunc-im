package com.ouyunc.im.processor;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.encrypt.Encrypt;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.LoginContent;
import com.ouyunc.im.utils.IdentityUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 登录消息处理器，这个处理类是第一条连接后发送的第一条消息类型，之后才能发送心跳等业务消息
 * @Version V3.0
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

    /**
     * 登录消息的前置处理
     * 注意：正式中建议使用登录认证
     * @param ctx
     * @param packet
     */
    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 这里判断该消息是否需要
        log.info("正在处理预登录消息...");
        // 取出登录消息
        final Message loginMessage = (Message) packet.getMessage();
        // 如果不是登录内容类型，则直接返回
        if (MessageContentEnum.LOGIN_CONTENT.type() != loginMessage.getContentType()) {
            return;
        }
        //将消息内容转成message
        LoginContent loginContent = JSONUtil.toBean(loginMessage.getContent(), LoginContent.class);
        // 做登录参数校验
        //1,进行参数合法校验，校验失败，结束 ；2,进行签名的校验，校验失败，结束
        // 根据appKey 获取appSecret 然后拼接
        // @todo 注意这里先写死 appKey 获取appSecret
//        String rawStr = loginContent.getAppKey() + IMConstant.AND + loginContent.getIdentity() + IMConstant.AND + loginContent.getCreateTime() + IMConstant.UNDERLINE + "ouyunc";
//        if (!validate(loginContent) || !Encrypt.AsymmetricEncrypt.prototype(loginContent.getSignatureAlgorithm()).validate(rawStr, loginContent.getSignature())) {
//            ctx.close();
//            return;
//        }

        final String comboIdentity = IdentityUtil.generalComboIdentity(loginContent.getIdentity(), packet.getDeviceType());
        //如果之前已经登录（重复登录请求），这里判断是否已经登录过,同一个账号在同一个设备不能同时登录
        //1,从分布式缓存取出该登录用户
        LoginUserInfo loginUserInfo = IMServerContext.LOGIN_USER_INFO_CACHE.get(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.LOGIN + comboIdentity);
        //2,从本地用户注册表中取出该用户的channel
        final ChannelHandlerContext bindCtx = IMServerContext.USER_REGISTER_TABLE.get(comboIdentity);
        // 如果是都不为空是重复登录请求，则不做处理，否则重新做登录
        // 下面处理登录消息的一下情况，某一个为空或都为空，无论那种情况都将之前的数据进行清除，重新添加登录信息
        if (loginUserInfo == null || bindCtx == null) {
            // 如果有，清空登录数据信息,解绑用户
            IMServerContext.LOGIN_USER_INFO_CACHE.delete(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.LOGIN + comboIdentity);
            ChannelHandlerContext ctx0 = IMServerContext.USER_REGISTER_TABLE.get(comboIdentity);
            if (ctx0 != null) {
                IMServerContext.USER_REGISTER_TABLE.delete(comboIdentity);
                ctx0.close();
            }
            // 注意：其实可以直接在这里调用登录的逻辑不需要往下面传递了，这里我继续往下面传递各尽职责
            ctx.fireChannelRead(packet);
        }
    }

    /**
     *  登录消息的处理；未开启登录认证，在接收到登录消息后不会走这里了，也就是没有往下面传递
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("正在处理登录消息...");
        // 注意：消息务必携带登录设备类型; 这里使用客户端唯一标识+登录设备类型作为新的唯一标识进行绑定，支持多设备端登录及同步消息,
        // 用户登录成功后，用户绑定
        Message message = (Message) packet.getMessage();
        LoginContent loginContent = JSONUtil.toBean(message.getContent(), LoginContent.class);
        UserHelper.bind(loginContent.getIdentity(), packet.getDeviceType(), ctx);
    }

}
