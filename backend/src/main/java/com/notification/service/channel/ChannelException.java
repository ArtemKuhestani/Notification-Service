package com.notification.service.channel;

/**
 * Исключение при ошибке отправки через канал.
 */
public class ChannelException extends RuntimeException {
    
    private final String errorCode;
    private final boolean retryable;
    
    public ChannelException(String message) {
        super(message);
        this.errorCode = "UNKNOWN";
        this.retryable = true;
    }
    
    public ChannelException(String message, String errorCode, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
    
    public ChannelException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "UNKNOWN";
        this.retryable = true;
    }
    
    public ChannelException(String message, String errorCode, boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * Можно ли повторить попытку отправки.
     */
    public boolean isRetryable() {
        return retryable;
    }
}
