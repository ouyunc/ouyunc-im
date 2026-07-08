# 消息投递渠道（channel）

## 模型

| 维度 | 规则 |
|------|------|
| IM 用户 | 同一 `userId` 多设备漫游（`deviceType`），`channel=IM(1)` |
| 外渠用户 | WhatsApp / Telegram 各为独立 `userId`、独立好友/群成员 |
| `FriendEntity.channel` | 发送方好友列表中，**对端**走哪条投递通道 |
| `GroupUserEntity.channel` | 该群成员的投递通道 |

枚举：`MessageDeliveryChannelEnum` — `1=IM`, `2=WHATSAPP`, `3=TELEGRAM`, `4=LINE`。

## 下行路由

持久化成功后：

1. 单聊/客服：`MessageDeliveryRouter.deliverPeerMessage`
   - 查 `friend(sender, recipient).channel`
   - `IM` → 长连接推送到 recipient 所有在线设备
   - 外渠 → Kafka `ouyunc_external_channel_outbound`（`ExternalChannelOutboundPayload`）
2. 群聊：按成员 `GroupUserEntity.channel` 分别 IM 推送或外渠 Kafka

## 防回声

外渠下行仅跳过 **接收方 = 发送方本人**（不把同一条外渠入站上行再发回给发送者）。

混合群：客户 A 从 WhatsApp 发言时，客户 B（同为 `channel=WHATSAPP`）仍通过 Kafka 外发投递；发送方在 `deliverGroupMembers` 中已排除。

## HTTP 入站

`/api/im/message/push` 的 `extra` 支持：

- `ingressChannel`: `whatsapp` / `telegram` → 设置 `metadata.ingressSource`
- `externalMessageId`: 厂商消息 ID

## 客服绑定

通常只维护 `friend(坐席, 客户).channel=WHATSAPP`；客户上行推坐席时无反向好友记录，默认 `IM`。
