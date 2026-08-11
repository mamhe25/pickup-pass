package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
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

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal FirebaseUserDetails user) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uid", user.getUid());
        body.put("role", user.getRole());
        if (user.getSchoolId() != null) body.put("schoolId", user.getSchoolId());
        body.put("status", "active");
        return ResponseEntity.ok(body);
    }
}
