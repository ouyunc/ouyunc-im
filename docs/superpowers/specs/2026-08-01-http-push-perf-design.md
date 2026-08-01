# HTTP Push 幂等（方案 1）

## 模型

状态仅「无 / 已成功」：校验通过后再 `SETNX(packetId)`，无 PENDING。

```
exists? → DUPLICATE
preProcess（失败不占位）
SETNX → ACCEPTED + 后台投递
SETNX 失败且已有值 → DUPLICATE
SETNX 失败且无值 → PROCESSING（可重试）
提交后台失败 → 释放占位 → 500
```

verify 使用 `ThreadPoolManager` 的 `http-push-verify`（VIRTUAL）；`ACCEPTED`/`DUPLICATE` = 成功。
