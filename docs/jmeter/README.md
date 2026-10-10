# ouyunc-im JMeter 压测说明

## 1. 压测范围

`ouyunc-im-http-push-load.jmx` 使用 JMeter 标准组件压测：

```text
POST /api/im/message/push
```

覆盖 HTTP 鉴权、请求转换、幂等、MQ SAVE 确认、Redis 热写、未读及在线投递入口。服务端返回 HTTP 200 仍不一定代表消息受理成功，因此计划会继续校验：

```text
success == true
data.status == ACCEPTED
```

`UNKNOWN`、`RETRY_LATER`、`REJECTED` 都计为业务失败。

默认 `push_type=0`，即系统通知单播。JWT 需要包含与 `X-App-Key` 一致的 `appKey`、合法的 `fromType`，以及 `im:push` 或系统通知对应权限。需要压测单聊时改为 `-Jpush_type=1`，并保证好友校验、发送方和接收方测试数据满足业务规则。

## 2. 前置条件

1. 使用 JMeter 5.5 或更高版本，JDK 17 或更高版本。
2. 目标环境必须使用专门的压测租户和压测账号。
3. 准备 HTTP Push JWT，不要把真实 token 写进 JMX 或提交到 Git。
4. 先确认一条请求可以正常返回 `ACCEPTED`。
5. 关闭 JMeter GUI Listener，正式压测必须使用非 GUI 模式。
6. JMeter 发压机与 IM 节点分开部署，避免客户端 CPU、网络与服务端争抢资源。

## 3. 快速验证

Windows PowerShell：

```powershell
jmeter -n `
  -t "docs/jmeter/ouyunc-im-http-push-load.jmx" `
  -Jhost=127.0.0.1 `
  -Jport=6003 `
  -Japp_key=load-test `
  -Jjwt="<HTTP_PUSH_JWT>" `
  -Jthreads=20 `
  -Jtarget_tps=10 `
  -Jramp_seconds=10 `
  -Jduration_seconds=60 `
  -l "target/jmeter-smoke.jtl" `
  -e -o "target/jmeter-smoke-report"
```

Linux：

```bash
jmeter -n \
  -t docs/jmeter/ouyunc-im-http-push-load.jmx \
  -Jhost=127.0.0.1 \
  -Jport=6003 \
  -Japp_key=load-test \
  -Jjwt='<HTTP_PUSH_JWT>' \
  -Jthreads=20 \
  -Jtarget_tps=10 \
  -Jramp_seconds=10 \
  -Jduration_seconds=60 \
  -l target/jmeter-smoke.jtl \
  -e -o target/jmeter-smoke-report
```

报告目录必须不存在或为空。重复执行前请换一个目录。

## 4. 参数

| 参数 | 默认值 | 说明 |
|---|---:|---|
| `protocol` | `http` | HTTP 或 HTTPS |
| `host` | `127.0.0.1` | IM HTTP 地址，不带协议 |
| `port` | `6003` | IM HTTP 端口 |
| `path` | `/api/im/message/push` | HTTP Push 路径 |
| `app_key` | `ouyunc` | 压测租户，必须与 JWT claim 一致 |
| `jwt` | 无 | 必填，HTTP Push Bearer JWT |
| `engine_id` | `jmeter-1` | 多发压机必须各不相同，参与 messageId |
| `threads` | `100` | JMeter 并发线程数 |
| `target_tps` | `100` | 全线程合计目标请求 TPS |
| `ramp_seconds` | `60` | 并发爬升时间 |
| `duration_seconds` | `300` | 稳态持续时间 |
| `push_type` | `0` | `0` 系统单播、`1` 单聊、`2` 群聊、`3` 客服、`4` 广播 |
| `push_channel` | `0` | `0` 为站内 IM |
| `to_type` | `1` | 默认用户类型 |
| `fixed_to` | 空 | 非空时所有请求发给同一目标，用于热点测试 |
| `to_prefix` | `load-user-` | `fixed_to` 为空时，按线程生成目标用户 |
| `content_size` | `256` | 文本正文字符数，建议分别测试 256、1024、4096 |
| `connect_timeout` | `2000` | 建连超时，毫秒 |
| `response_timeout` | `15000` | 响应超时，毫秒，需覆盖 MQ 归档确认上限 |

每次请求都会生成：

```text
messageId = engine_id + UUID
requestId = messageId
```

多发压机时 `engine_id` 必须唯一，例如 `jmeter-a`、`jmeter-b`，避免结果排查时混淆来源。

## 5. 建议压测阶梯

先预热 5 分钟，再执行每档至少 10 分钟：

| 阶段 | threads | target_tps | duration | 目的 |
|---|---:|---:|---:|---|
| 冒烟 | 20 | 10 | 60s | 验证鉴权、数据和断言 |
| 基线 | 100 | 100 | 600s | 获取低负载延迟 |
| 一级 | 200 | 500 | 600s | 检查 Redis/MQ 线性增长 |
| 二级 | 400 | 1000 | 600s | 检查业务池和连接池 |
| 三级 | 800 | 2000 | 900s | 接近中型节点目标 |
| 四级 | 1200+ | 3000～5000 | 900s | 需要多台发压机 |
| 极限 | 按需 | 每档增加 20% | 600s/档 | 找到拐点，不作为稳定容量 |

稳定容量取满足以下条件的最高一档，再保留至少 30% 余量：

```text
业务错误率 < 0.1%
HTTP P99 < 目标阈值
data.status=ACCEPTED 比例 >= 99.9%
CPU 长期 < 70%
JVM Heap 长期 < 70%
Redis/MQ 无持续积压
业务池和连接池无持续满载
```

## 6. 场景拆分

### 均匀目标

不传 `fixed_to`，每个线程使用独立目标，测试常规分散流量：

```text
-Jto_prefix=load-user-
```

### 热点用户

所有请求发送给同一个用户，验证单收件人槽位、未读和路由热点：

```text
-Jfixed_to=hot-user-1
```

热点测试应单独执行，不能与稳定容量数字混用。

### 单聊

```text
-Jpush_type=1
```

JWT 需要单聊权限；测试发送方和目标用户需要满足当前好友关系校验。

### 群聊

```text
-Jpush_type=2 -Jfixed_to=<groupId> -Jto_type=2
```

分别测试 10、100、500、2000、20000 人群。容量报告必须同时记录消息 TPS 和实际下行目标 TPS。

### 客服消息

当前 JMX 的请求体未填写 `correlationId`。客服压测需要复制 HTTP Sampler，并增加有效 ticketId；JWT 需要客服推送权限，`toType` 按坐席或访客设置。

## 7. 服务端同步观测

JMeter 只能看到入口响应，压测期间必须同步采集：

- JVM：CPU、Heap、Direct Memory、GC 暂停、虚拟线程数。
- Netty：连接数、EventLoop pending task、不可写 Channel 数。
- 线程池：in-flight、capacity、rejectedCount。
- ChannelOrderedTasks：queue overflow、queue wait timeout、deadline timeout。
- Redis：ops/s、连接池等待、命令 P95/P99、Lua P99、slowlog、CPU、内存、eviction。
- Kafka/RocketMQ：produce P95/P99、错误率、topic TPS、consumer lag。
- 数据库：归档 TPS、批量大小、事务耗时、连接池、慢 SQL、主从延迟。
- QoS：重试任务数、ACK pending/in-flight、取消转发失败。
- 业务：ACCEPTED、UNKNOWN、RETRY_LATER、REJECTED、重复消息、最终落库数。

压测结束后必须对账：

```text
JMeter ACCEPTED 数
MQ SAVE 消息数
im_message 去重后新增数
Redis/数据库按 messageId 缺失数
```

异步消费者存在延迟时，应等待 consumer lag 归零后再对账。

## 8. WebSocket 长连接压测说明

仓库原有 `docs/jmetter/ouyunc-im websocket 压测计划.jmx` 依赖 `eu.luminis.jmeter.wssampler` 插件，并包含固定二进制登录包、旧地址、旧路径、本机绝对文件路径和多个 GUI Listener，不能直接用于当前版本的正式容量测试。

JMeter 线程模型也不适合单机模拟数十万长期 WebSocket。建议：

1. JMeter 只做 1 万以内的协议正确性和中小规模连接测试。
2. 5 万以上连接使用基于 Netty/Vert.x 的专用压测客户端，多进程、多发压机运行。
3. 登录包必须按当前 Packet 协议动态生成 identity、createTime 和签名。
4. 测试连接保持、心跳、单聊、ACK、重连风暴和节点摘流，不只测试握手成功。
5. 每台发压机先测自身 CPU、内存和端口上限，确保瓶颈不在发压端。

## 9. 注意事项

- 不要在生产真实租户上执行压测。
- 不要在 GUI 模式执行大流量测试。
- 每次压测使用全新的 `messageId`；幂等重试测试除外。
- 幂等重试测试必须保持请求正文不变，否则服务端应返回冲突。
- 测试大正文时关注 Netty Direct Memory 和网络带宽。
- 若压测机达到 80% CPU，增加发压机并使用不同 `engine_id`。
- 稳定容量不是极限 TPS，应至少保留 30% 资源余量和单节点故障接管空间。
