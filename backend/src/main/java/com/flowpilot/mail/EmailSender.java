package com.flowpilot.mail;

public interface EmailSender {
    /**
     * Hands the message to the provider. Returning normally means the provider ACCEPTED the request;
     * it does not prove inbox delivery.
     */
    void send(OutgoingEmail email) throws EmailDeliveryException;
}
