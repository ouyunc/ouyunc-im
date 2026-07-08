# 已读回执与会话 Offset 协议说明

本文描述客户端与服务端对「会话已读水位（`session_message_offset`）」与「对方已读展示」的约定，适用于单聊与群聊。

## 1. 核心概念

| 概念 | 说明 |
|------|------|
| `packetId` | 协议包雪花 ID，会话内按时间单调递增，用作消息序号与 offset |
| `session_message_offset` | 当前用户在某会话、某设备上已处理到的最大 `packetId`（含自己发的消息） |
| 已读回执 | `contentType = -7`（`READ_RECEIPT_CONTENT`），用于推进 offset **并**（单聊）通知对端 |
| 静默 offset | 发送聊天消息成功后，服务端仅更新发送方本端 offset，**不**向对端投递已读回执 |

### 存储（Redis）

```
sro:{appKey}:{identityType}:{from}:{deviceType}:{to}
```

- `from`：阅读者（offset 归属用户）
- `to`：单聊为对端用户 ID，群聊为群 ID
- `identityType`：`1` 单聊，`2` 群聊
- **按设备独立**（方案 B）：各端 offset 互不影响，校验仅与本设备已存值比较，写入使用 Lua 脚本取 `max` 单调递增

持久化：`ouyunc_im_session_message_offset`（MySQL / MongoDB），由 MQ 异步落库。

---

## 2. 已读回执报文

### 2.1 字段

| 字段 | 单聊 | 群聊 |
|------|------|------|
| `from` | 阅读者 | 阅读者 |
| `to` | 对端用户 ID | 群 ID |
| `contentType` | `-7` | `-7` |
| `content` | JSON 数组，元素为 `packetId` | 同左 |

### 2.2 `content` 语义

传**会话内已处理到的消息 `packetId` 列表**；服务端取 **最大值** 作为 offset。

**推荐**：只传一个 id——当前会话最后一条已处理消息的 `packetId`（可包含自己发的消息）。

```json
[9876543210987654321]
```

### 2.3 校验规则

1. `content` 中的 `packetId` 必须存在于该会话 ZSet 索引中（可为自己或对方消息）
2. `max(content)` ≥ 本设备已存 offset（不可回退）
3. 不校验「必须为对方消息」（`isValidSender = false`）

### 2.4 服务端行为

| 动作 | 单聊 | 群聊 |
|------|------|------|
| 更新本端 offset | ✅ | ✅ |
| 推送给对端 / 群成员 | ✅ 推送给 `to`（原消息发送方） | ❌ 不广播（避免推送风暴） |
| selfSync | 视配置同步阅读方其它设备 | 视配置同步阅读方其它设备 |

群聊「谁已读到哪」需 HTTP 拉取各成员 offset，或产品层不做逐条已读。

---

## 3. 两种推进 offset 的路径

| 场景 | 客户端 | 服务端 | 对端是否实时知晓 |
|------|--------|--------|------------------|
| **只看不回** | 发已读回执 | 更新 offset + 单聊推送回执 | ✅（单聊） |
| **发消息** | 可选本地更新 | 持久化成功后 **静默** 更新发送方 offset | ❌（靠 §4 推断或 HTTP 拉取） |

### 3.1 只看不回

在打开会话、滚到底、离开会话等时机，若 offset 有变化则发送已读回执。

### 3.2 发消息（静默）

聊天消息（含撤回等写入会话的消息）持久化成功后，服务端调用 `advanceSenderReadOffsetOnSend`：

- 将发送方 `(from, deviceType, to)` 的 offset 推进到该消息 `packetId`
- **不**投递已读回执给对端

客户端在收到发送 ACK 后也应更新本地 offset。若随后水位未变，**无需**再为同一条水位重复发已读回执。

---

## 4. 对端如何判断「我方消息已被对方读过」（单聊）

对端不只有已读回执一种信号。客户端应维护：

```text
peerReadWatermark = max(
  已读回执中对方上报的 offset,
  对方最近发来聊天消息的 packetId
)
```

对**我方发送**的每条消息 `myMsgId`：

```text
已读  ⟺  peerReadWatermark >= myMsgId
未读  ⟺  peerReadWatermark < myMsgId
```

### 4.1 收到对方聊天消息时（无已读回执）

收到对方普通消息 `incomingPacketId` 时：

```text
peerReadWatermark = max(peerReadWatermark, incomingPacketId)
```

若 `incomingPacketId > myMsgId`，则 `myMsgId` 可标为已读。

**依据**：雪花 `packetId` 按时间递增；对方后发消息 id 通常大于我方先发消息 id，可推断对方已看到此前内容。

### 4.2 收到已读回执时

解析 `content` 取 max 作为对方 offset，更新 `peerReadWatermark`。

### 4.3 注意

1. 依赖 `packetId` 全局时间单调；时钟回拨可能导致极少数乱序。
2. 对方未读最新消息就回复时，可能误判已读；严格场景需依赖已读回执。
3. 群聊不向全员推已读，发送方展示需 HTTP 拉成员 offset 或不做逐条已读。

---

## 5. 离线拉取

- 拉取起点：本设备 `(from=self, deviceType, to=会话对端/群)` 的 `session_message_offset`
- 拉取条件：`packetId > offset` 的会话消息
- 发消息、已读回执均会推进本设备 offset；换机/重装以服务端该 key 为准

---

## 6. 客户端检查清单

- [ ] 只看不回：offset 变化时发已读回执（`content` 为会话最后 `packetId`）
- [ ] 发消息成功：更新本地 offset；不必重复发同水位回执
- [ ] 展示对方已读：`peerReadWatermark = max(回执 offset, 对方最新消息 id)`
- [ ] 单聊：监听已读回执推送更新水位
- [ ] 群聊：不依赖回执推送；按需 HTTP 拉 offset
- [ ] 多设备：各端 offset 独立；若查询对方已读可对对方各 `deviceType` 取 max

---

## 7. 服务端实现索引

| 模块 | 说明 |
|------|------|
| `ReadReceiptSupport` | 已读校验、offset 更新、发消息静默推进 |
| `One2OneMessageProcessor` | 单聊已读回执推送对端；发消息后静默 offset |
| `GroupMessageProcessor` | 群已读仅写 offset；发消息后静默 offset |
| `CustomerServiceMessageProcessor` | 客服会话，行为同单聊 |

---

## 8. 版本

- 文档版本：6.5.x
- 与「按设备 offset（方案 B）」「发消息静默更新」「已读 content 可含己方消息」一致
