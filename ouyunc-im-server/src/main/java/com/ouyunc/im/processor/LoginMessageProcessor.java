package com.ouyunc.im.processor;


import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.cache.l1.distributed.redis.redisson.RedissonFactory;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.DeviceEnum;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.constant.enums.NetworkEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.domain.ImAppDetail;
import com.ouyunc.im.encrypt.Encrypt;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.LoginContent;
import com.ouyunc.im.packet.message.content.ServerNotifyContent;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SnowflakeUtil;
import com.ouyunc.im.utils.SystemClock;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 登录消息处理器，这个处理类是第一条连接后发送的第一条消息类型，之后才能发送心跳等业务消息
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
        if (StringUtils.isEmpty(loginContent.getIdentity()) || StringUtils.isEmpty(loginContent.getAppKey()) || StringUtils.isEmpty(loginContent.getSignature())  || loginContent.getCreateTime() <= 0 || Encrypt.AsymmetricEncrypt.prototype(loginContent.getSignatureAlgorithm()) == null) {
            return false;
        }
        // raw = appkey&identity&createtime_appSecret
        // 通过 appKey 在缓存或数据库中获取账户及权限信息，然后进行计算校验
        ImAppDetail imAppDetail = DbHelper.getAppDetail(loginContent.getAppKey());
        if (imAppDetail == null) {
            return false;
        }
        String rawStr = loginContent.getAppKey() + IMConstant.AND + loginContent.getIdentity() + IMConstant.AND + loginContent.getCreateTime() + IMConstant.UNDERLINE + imAppDetail.getAppSecret();
        if (!Encrypt.AsymmetricEncrypt.prototype(loginContent.getSignatureAlgorithm()).validate(rawStr, loginContent.getSignature())) {
            return false;
        }
        // 是否开启
        if (IMServerContext.SERVER_CONFIG.isLoginMaxConnectionValidateEnable() &&  IMConstant.MINUS_ONE.equals(imAppDetail.getImMaxConnections())) {
            // 做权限校验，比如，同一个appKey 只允许在线10个连接
            Integer connections = DbHelper.getCurrentAppImConnections(loginContent.getAppKey());
            // 计数从0开始,不能超过最大连接数
            if (++connections >= imAppDetail.getImMaxConnections()) {
                return false;
            }
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
        LoginContent loginContent = JSON.parseObject(loginMessage.getContent(), LoginContent.class);
        // 做登录参数校验
        //1,进行参数合法校验，校验失败，结束 ；2,进行签名的校验，校验失败，结束，3，进行权限校验，校验失败，结束
        // 根据appKey 获取appSecret 然后拼接
        RLock lock = RedissonFactory.INSTANCE.redissonClient().getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP + loginContent.getAppKey());
        lock.lock();
        try{
            if (IMServerContext.SERVER_CONFIG.isLoginValidateEnable() && !validate(loginContent)) {
                log.warn("客户端id: {} 登录参数: {}，校验未通过！", ctx.channel().id().asShortText(), JSON.toJSONString(loginContent));
                ctx.close();
                return;
            }
            final String comboIdentity = IdentityUtil.generalComboIdentity(loginContent.getIdentity(), packet.getDeviceType());
            //如果之前已经登录（重复登录请求），这里判断是否已经登录过,同一个账号在同一个设备不能同时登录
            //1,从分布式缓存取出该登录用户
            LoginUserInfo loginUserInfo = IMServerContext.LOGIN_USER_INFO_CACHE.getHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.LOGIN + loginContent.getIdentity(), DeviceEnum.getDeviceNameByValue(packet.getDeviceType()));
            //2,从本地用户注册表中取出该用户的channel
            final ChannelHandlerContext bindCtx = IMServerContext.USER_REGISTER_TABLE.get(comboIdentity);
            // 如果是都不为空是重复登录请求(1，不同的设备远程登录，2，同一设备重复发送登录请求)，向原有的连接发送通知，有其他客户端登录，并将其连接下线
            if (bindCtx != null) {
                // 给原有连接发送通知消息，并将其下线，添加新的连接登录
                Message message = new Message(IMServerContext.SERVER_CONFIG.getLocalHost(), loginContent.getIdentity(), MessageContentEnum.SERVER_NOTIFY_CONTENT.type(), JSON.toJSONString(new ServerNotifyContent(String.format(IMConstant.REMOTE_LOGIN_NOTIFICATIONS, packet.getIp()))), SystemClock.now());
                // 注意： 这里的原来的连接使用的序列化方式，应该是和新连接上的序列化方式一致，这里当成一致，当然不一致也可以做，后面遇到再改造
                Packet notifyPacket = new Packet(packet.getProtocol(), packet.getProtocolVersion(), SnowflakeUtil.nextId(), DeviceEnum.PC_LINUX.getValue(), NetworkEnum.OTHER.getValue(), IMServerContext.SERVER_CONFIG.getLocalHost(), MessageEnum.IM_SERVER_NOTIFY.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), packet.getSerializeAlgorithm(),  message);
                MessageHelper.sendMessageSync(notifyPacket, comboIdentity);
                IMServerContext.USER_REGISTER_TABLE.delete(comboIdentity);
                bindCtx.close();
            }
            if (loginUserInfo != null) {
                IMServerContext.LOGIN_USER_INFO_CACHE.deleteHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.LOGIN + loginContent.getIdentity(), DeviceEnum.getDeviceNameByValue(packet.getDeviceType()));
            }
            // 绑定信息,不在往下传递
            UserHelper.bind(loginContent.getAppKey(), loginContent.getIdentity(), packet.getDeviceType(), ctx);
        }finally {
            lock.unlock();
        }
        // 注意：其实可以直接在这里调用登录的逻辑不需要往下面传递了，这里我继续往下面传递各尽职责,最大优化，这里不再往下传递，
        // ctx.fireChannelRead(packet);
    }


    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        // do nothing
    }
}
