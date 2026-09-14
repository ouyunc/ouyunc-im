package com.ouyunc.base.constant;

/**
 * HTTP 签名凭证中的 scope。租户推送与节点运维权限必须分开签发，不能把有效 appKey 当作运维身份。
 */
public final class HttpAuthScopeConstant {

    private HttpAuthScopeConstant() {
    }

    /** 推送管理员：放行全部 pushType。 */
    public static final String IM_PUSH_ADMIN = "im:push:admin";

    /** 通用推送：单聊 / 群聊 / 客服（不含系统通知）。 */
    public static final String IM_PUSH = "im:push";

    /** 系统通知（含广播）。 */
    public static final String IM_PUSH_SYSTEM = "im:push:system";

    /** 单聊推送。 */
    public static final String IM_PUSH_ONE2ONE = "im:push:one2one";

    /** 群聊推送。 */
    public static final String IM_PUSH_GROUP = "im:push:group";

    /** 客服推送。 */
    public static final String IM_PUSH_CS = "im:push:cs";

    /**
     * 节点运维：摘流 / 取消摘流 / 通知本机客户端重连。
     * 须使用独立运维 JWT 密钥签发，不得与业务推送密钥共用。
     */
    public static final String IM_ADMIN_DRAIN = "im:admin:drain";

    /**
     * 平台身份：可在租户接口中指定其他 appKey。
     * 仅有租户凭证时必须使用 Principal.appKey。
     */
    public static final String IM_ADMIN_PLATFORM = "im:admin:platform";

    /** 本租户关系本机缓存失效（可集群扇出）。 */
    public static final String IM_RELATION_CACHE = "im:relation-cache";
}
