package com.mudhut.nudge.email;

public interface IEmailService {
    void sendEmail(String to, String subject, String content);
}
