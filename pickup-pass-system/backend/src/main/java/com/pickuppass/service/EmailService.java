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
     *         missing SMTP credentials). Deliberately never throws: callers
     *         decide whether email is optional or required for their workflow.
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
                "Once activated, open My Profile and upload a clear verification photo. " +
                "PickupPass checks that one clear human face is visible before the photo is accepted. " +
                "QR pickup passes stay locked until an accepted verification photo is on your account.\n\n" +
                "Use a recent front-facing photo of yourself with good lighting and no mask, dark sunglasses, " +
                "group photo, screenshot, pet, scenery, or cartoon.\n\n" +
                "If you weren't expecting this, please contact your school office."
        );
        return trySend(message, toEmail, "parent invite");
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
        return trySend(message, toEmail, "staff invite");
    }

    /**
     * Email ownership is mandatory before a public demo inquiry becomes visible
     * to Platform Owners. The verification token itself is never persisted in
     * Firestore; DemoRequestService stores only its SHA-256 digest.
     */
    public boolean sendDemoVerification(
            String toEmail,
            String contactName,
            String organization,
            String verificationLink,
            int expiresMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Verify your PickupPass demo request");
        message.setText(
                "Hi " + contactName + ",\n\n" +
                "We received a request for a PickupPass walkthrough for " + organization + ".\n\n" +
                "Verify that you own this email address before the inquiry is sent to the PickupPass team:\n\n" +
                verificationLink + "\n\n" +
                "This verification link expires in " + expiresMinutes + " minutes. " +
                "If it expires, the verification page can send a fresh link.\n\n" +
                "If you did not request a PickupPass demo, you can ignore this email."
        );
        return trySend(message, toEmail, "demo verification");
    }

    private boolean trySend(SimpleMailMessage message, String toEmail, String purpose) {
        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            log.warn("Could not send {} email to {}: {}", purpose, toEmail, e.getMessage());
            return false;
        }
    }
}
