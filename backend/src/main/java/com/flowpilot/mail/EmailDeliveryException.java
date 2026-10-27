package com.flowpilot.mail;

/** The provider did not accept the request. Never contains tokens or API keys. */
public class EmailDeliveryException extends RuntimeException {
    private final int status;

    public EmailDeliveryException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** HTTP status from the provider, or -1 for network failures/timeouts. */
    public int status() { return status; }
}
