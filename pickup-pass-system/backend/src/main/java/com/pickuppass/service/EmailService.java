package com.pickuppass.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:no-reply@pickuppass.app}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * @return true if the email was sent, false if sending failed (e.g. bad/
     *         missing SMTP credentials). Deliberately never throws: the
     *         account (Firebase Auth user + Firestore profile) is always
     *         created BEFORE this is called, so a mail-server hiccup should
     *         never turn an otherwise-successful account creation into an
     *         error response. Callers surface the false case to the client
     *         so the admin knows to tell the person to use "Forgot password?"
     *         manually instead of waiting on an email that never arrives.
     */
    public boolean sendParentInvite(String toEmail, String parentName, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("You've been added as a pickup contact");
        message.setText(
                "Hi " + parentName + ",\n\n" +
                "Your school has registered you as an authorized pickup contact in the " +
                "Digital Pickup Pass System. Set your password to activate your account:\n\n" +
                resetLink + "\n\n" +
                "Once activated, you'll be able to upload a profile photo and generate a " +
                "secure QR pass for school pickups.\n\n" +
                "If you weren't expecting this, please contact your school office."
        );
        return trySend(message, toEmail);
    }

    public boolean sendStaffInvite(String toEmail, String name, String role, String resetLink) {
        String roleLabel = switch (role) {
            case "school_admin" -> "school administrator";
            case "teacher" -> "teacher/staff";
            case "master_admin" -> "master administrator";
            default -> role;
        };

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Your Pickup Pass staff account is ready");
        message.setText(
                "Hi " + name + ",\n\n" +
                "An account has been created for you in the Digital Pickup Pass System " +
                "with " + roleLabel + " access. Set your password to activate it:\n\n" +
                resetLink + "\n\n" +
                "If you weren't expecting this, please contact your school administrator."
        );
        return trySend(message, toEmail);
    }

    private boolean trySend(SimpleMailMessage message, String toEmail) {
        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.warn("Could not send invite email to {}: {}. The account was still created — " +
                     "they can use 'Forgot password?' on the sign-in page instead.", toEmail, e.getMessage());
            return false;
        }
    }
}
