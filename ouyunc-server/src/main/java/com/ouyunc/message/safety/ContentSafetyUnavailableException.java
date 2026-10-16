package com.ouyunc.message.safety;

/**
 * 文本审核服务不可用。调用方应暂缓受理并让客户端用同一 messageId 重试，不能把这次失败当成审核通过。
 */
public final class ContentSafetyUnavailableException extends RuntimeException {

    /**
     * @param message 不含正文的原因
     */
    public ContentSafetyUnavailableException(String message) {
        super(message);
    }

    /**
     * @param message 不含正文的原因
     * @param cause   原始异常，日志只保留类型和错误码
     */
    public ContentSafetyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 沿 cause 链判断是不是审核服务不可用。
     *
     * @param error 入口捕获的异常
     * @return true 时应返回 40012，不落库
     */
    public static boolean causedBy(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof ContentSafetyUnavailableException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
