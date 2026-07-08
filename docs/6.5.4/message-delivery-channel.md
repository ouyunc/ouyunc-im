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

1. 单聊：`MessageDeliveryRouter.deliverPeerMessage`
   - 查 `friend(sender, recipient).channel`
   - `IM` → 长连接推送到 recipient 所有在线设备
   - 外渠 → Kafka `ouyunc_external_channel_outbound`（`ExternalChannelOutboundPayload`）
2. 客服：`CsMessageDeliveryRouter.deliverCustomerServiceMessage`
   - **不查好友表**；坐席始终 IM 长连接
   - 访客按 Redis route Hash 的 `channel`（与 `cs_consultation_ticket.channel` 对齐）：
     - `im` / `web` / `app` / `h5` / `pc` → IM
     - `whatsapp` / `telegram` / `line` → Kafka `ouyunc_external_channel_outbound`
3. 群聊：按成员 `GroupUserEntity.channel` 分别 IM 推送或外渠 Kafka

## 防回声

外渠下行仅跳过 **接收方 = 发送方本人**（不把同一条外渠入站上行再发回给发送者）。

混合群：客户 A 从 WhatsApp 发言时，客户 B（同为 `channel=WHATSAPP`）仍通过 Kafka 外发投递；发送方在 `deliverGroupMembers` 中已排除。

## HTTP 入站

`/api/im/message/push` 的 `extra` 支持：

- `ingressChannel`: `whatsapp` / `telegram` → 设置 `metadata.ingressSource`
- `externalMessageId`: 厂商消息 ID

## 客服投递

客服会话与好友关系解耦。CS 分配坐席写入 Redis route Hash 时带上 `channel` 字段；IM 读 `CsImSessionRoute.channel` 决定访客下行渠道。

| ticket.channel | 访客下行 |
|----------------|----------|
| im / web / app / h5 / pc | IM 长连接 |
| whatsapp | Kafka → WhatsApp 适配 |
| telegram | Kafka → Telegram 适配 |
| line | Kafka → Line 适配 |

坐席侧始终 IM。已部署环境若 route 缺 `channel`，需重新分配/绑定 route 或等 ticket 关闭后新建。
