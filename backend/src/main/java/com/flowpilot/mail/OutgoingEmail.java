package com.flowpilot.mail;

public record OutgoingEmail(String toEmail, String toName, String subject, String html, String text) {}
