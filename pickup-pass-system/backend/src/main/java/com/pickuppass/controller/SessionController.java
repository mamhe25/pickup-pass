package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AccountEmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/session")
public class SessionController {
    private final AccountEmailService accountEmails;

    public SessionController() {
        this.accountEmails = null;
    }

    @Autowired

    public SessionController(AccountEmailService accountEmails) {
        this.accountEmails = accountEmails;
    }


    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal FirebaseUserDetails user) {
        if (accountEmails != null) {
            try {
                accountEmails.synchronize(user.getUid());
            } catch (Exception ignored) {
            // Profile email sync is best effort; identity response remains available.
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uid", user.getUid());
        body.put("role", user.getRole());
        if (user.getSchoolId() != null) body.put("schoolId", user.getSchoolId());
        body.put("status", "active");
        return ResponseEntity.ok(body);
    }
}
