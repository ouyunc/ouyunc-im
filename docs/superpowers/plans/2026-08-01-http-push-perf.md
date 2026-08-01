# HTTP Push Perf Implementation Plan

> **For agentic workers:** Implement tasks below; user approved A+A+A in chat.

**Goal:** Group validate dedupe + errorMessage contract + async verify pool.

**Files:**
- `HttpPushValidatorChain.java` — drop GroupUserValidator
- `MessagePushResponse.java` — docs
- `HttpRequestDispatcher.java` — CompletionStage support
- `ThreadPoolManager` / `http-push-verify` — verify 池统一管理
- `InternalPacketIngressService.java` — async preProcess
- `MessagePushController.java` — return Object
- `MessageServerProperties.java` + `ouyunc-server.yml` — verify threads
- `AbstractMessageServer.java` — shutdown

## Tasks

1. Remove GroupUserValidator from `buildGroupChecks`
2. Update MessagePushResponse / status enum docs for errorMessage
3. Add verify executor + properties
4. Refactor ingress to return CompletionStage for pending claim path
5. Dispatcher handles CompletionStage (sync + async dispatch)
6. Compile module
