package com.mudhut.nudge.email;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mailersend.sdk.MailerSend;
import com.mailersend.sdk.MailerSendResponse;
import com.mailersend.sdk.emails.Email;
import com.mailersend.sdk.exceptions.MailerSendException;
import com.mudhut.nudge.config.EnvConfig;

@Service
public class MailerSendEmailService implements IEmailService {

    @Autowired
    private EnvConfig envConfig;

    @Override
    public void sendEmail(String to, String subject, String content) {
        Email email = new Email();

        email.setFrom("name", "mudhutsoftware@gmail.com");

        email.addRecipient("name", to);

        email.setSubject(subject);

        email.setPlain(content);

        MailerSend ms = new MailerSend();

        String apiToken = envConfig.getMailerSendApiToken();

        ms.setToken(apiToken);

        try {
            MailerSendResponse response = ms.emails().send(email);
            System.out.println(response.messageId);
        } catch (MailerSendException e) {
            e.printStackTrace();
        }
    }
}
