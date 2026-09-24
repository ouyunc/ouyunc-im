package com.ouyunc.base.packet.message;

import com.ouyunc.base.model.Metadata;
import io.protostuff.Tag;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @author fzx
 * @description 消息
 */
public class Message implements Serializable, Cloneable {
    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * 客户端消息id
     */
    @Tag(1)
    private String id;

    /**
     * 发送者，唯一标识
     */
    @Tag(2)
    private String from;


    /**
     * 发送者标识类型，见 {@link com.ouyunc.base.constant.enums.MessageFromToTypeEnum}
     */
    @Tag(3)
    private int fromType;


    /**
     * 接收者，唯一标识
     */
    @Tag(4)
    private String to;

    /**
     * 接收者标识类型，见 {@link com.ouyunc.base.constant.enums.MessageFromToTypeEnum}
     */
    @Tag(5)
    private int toType;



    /**
     * 内容类型，如果登录的内容类型，聊天的消息内容类型（文本，语音，图片，视频...），webrtc 的信令内容类型
     */
    @Tag(6)
    private int contentType;


    /**
     * 具体内容json str
     */
    @Tag(7)
    private String content;


    /**
     * @xxx, 功能的实现，存放群聊的@人员所登录绑定的唯一标识集合
     */
    @Tag(8)
    private List<String> at;

    /**
     * 引用消息id 集合
     */
    @Tag(9)
    private List<String> ref;

    /**
     * 客户端扩展/附加字段，客户端自定义数据字段，如：消息发送时间、消息发送者昵称、消息发送者头像、消息发送者设备类型、消息发送者网络类型、消息发送者客户端版本、消息发送者客户端语言、消息发送者客户端平台、消息发送者客户端IP、消息发送者客户端MAC地址、消息发送者客户端操作系统、消息发送者客户端浏览器、消息发送者客户端浏览器版本、消息发送者客户端浏览器语言、消息发送
     */
    @Tag(10)
    private String extra;

    /**
     * 投递重试级别：0 不启用 QoS 幂等/下行重试；1 启用幂等和至少一次下行投递。
     * 发送方受理结果对两个级别均返回；收到 ACCEPTED 不代表接收方已收到。
     * 客户端对 qos=1 的下行消息仍须按 messageId 去重并回 C2S ACK。
     */
    @Tag(11)
    private int qos;

    /**
     * 客户端消息创建时间戳（毫秒）
     */
    @Tag(12)
    private long createTime;


    /**
     * 业务关联id,非必填
     */
    @Tag(13)
    private String correlationId;


    /**
     * 元数据，对内访问
     */
    @Tag(14)
    private Metadata metadata;


    /** JSON 字段名（与 JavaBean 属性名一致）。 */
    public static final class Fields {
        public static final String ID = "id";
        public static final String FROM = "from";
        public static final String FROM_TYPE = "fromType";
        public static final String TO = "to";
        public static final String TO_TYPE = "toType";
        public static final String CONTENT_TYPE = "contentType";
        public static final String CONTENT = "content";
        public static final String AT = "at";
        public static final String REF = "ref";
        public static final String EXTRA = "extra";
        public static final String QOS = "qos";
        public static final String CREATE_TIME = "createTime";
        public static final String CORRELATION_ID = "correlationId";
        public static final String METADATA = "metadata";

        private Fields() {
        }
    }

    public Message() {
    }

    public Message(String id, String from, String to, int contentType, long createTime) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.createTime = createTime;
    }

    public Message(String id, String from, String to, int contentType, String content, long createTime) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.createTime = createTime;
    }


    public Message(String id, String from, String to, int contentType, String content, long createTime, Metadata metadata) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.createTime = createTime;
        this.metadata = metadata;
    }
    public Message(String id, String from, String to, int contentType, String content, String extraData, int qos, long createTime) {
        this.id  = id;
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.extra = extraData;
        this.qos = qos;
        this.createTime = createTime;
    }

    public Message(String id, String from, String to, int contentType, String content, int qos, long createTime, Metadata metadata) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.qos = qos;
        this.createTime = createTime;
        this.metadata = metadata;
    }

    public Message(String id, String from, String to, int contentType, String content, String extraData, Metadata metadata, int qos, long createTime) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.extra = extraData;
        this.metadata = metadata;
        this.qos = qos;
        this.createTime = createTime;
    }

    public Message(String id, String from, String to, int contentType, String content, List<String> at, String extra, int qos, long createTime, Metadata metadata) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.at = at;
        this.extra = extra;
        this.qos = qos;
        this.createTime = createTime;
        this.metadata = metadata;
    }

    public Message(String id, String from, String to, int contentType, String content, List<String> at, List<String> ref, String extra, int qos, long createTime, Metadata metadata) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.at = at;
        this.ref = ref;
        this.extra = extra;
        this.qos = qos;
        this.createTime = createTime;
        this.metadata = metadata;
    }

    public Message(String id, String from, int fromType, String to, int toType, int contentType, String content, List<String> at, List<String> ref, String extra, int qos, long createTime, Metadata metadata) {
        this.id = id;
        this.from = from;
        this.fromType = fromType;
        this.to = to;
        this.toType = toType;
        this.contentType = contentType;
        this.content = content;
        this.at = at;
        this.ref = ref;
        this.extra = extra;
        this.qos = qos;
        this.createTime = createTime;
        this.metadata = metadata;
    }

    public Message(String id, String from, int fromType, String to, int toType, int contentType, String content, List<String> at, List<String> ref, String extra, int qos, long createTime, String correlationId, Metadata metadata) {
        this.id = id;
        this.from = from;
        this.fromType = fromType;
        this.to = to;
        this.toType = toType;
        this.contentType = contentType;
        this.content = content;
        this.at = at;
        this.ref = ref;
        this.extra = extra;
        this.qos = qos;
        this.createTime = createTime;
        this.correlationId = correlationId;
        this.metadata = metadata;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getRef() {
        return ref;
    }

    public void setRef(List<String> ref) {
        this.ref = ref;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public int getContentType() {
        return contentType;
    }

    public void setContentType(int contentType) {
        this.contentType = contentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Metadata getMetadata() {
        if (metadata == null) {
            metadata = new Metadata();
        }
        return metadata;
    }

    /**
     * 是否携带元数据。校验层禁止用 {@link #getMetadata()} 判断，避免把空壳当已赋值。
     */
    public boolean hasMetadata() {
        return metadata != null;
    }

    public Metadata getMetadataOrNull() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public List<String> getAt() {
        return at;
    }

    public void setAt(List<String> at) {
        this.at = at;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public int getFromType() {
        return fromType;
    }

    public void setFromType(int fromType) {
        this.fromType = fromType;
    }

    public int getToType() {
        return toType;
    }

    public void setToType(int toType) {
        this.toType = toType;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    @Override
    public Message clone() {
        try {
            Message message = (Message) super.clone();
            if (this.metadata != null) {
                message.setMetadata(this.metadata.clone());
            }
            return message;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    @Override
    public String toString() {
        return "Message{" +
                "  id='" + id + '\'' +
                ", from='" + from + '\'' +
                ", fromType='" + fromType + '\'' +
                ", to='" + to + '\'' +
                ", toType='" + toType + '\'' +
                ", contentType=" + contentType +
                ", content='" + content + '\'' +
                ", at='" + at + '\'' +
                ", ref='" + ref + '\'' +
                ", extra='" + extra + '\'' +
                ", qos=" + qos +
                ", createTime=" + createTime +
                ", correlationId=" + correlationId +
                ", metadata=" + metadata +
                '}';
    }
}
