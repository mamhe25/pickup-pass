package com.pickuppass.controller;

import com.pickuppass.service.DemoRequestCommunicationService;
import com.pickuppass.service.DemoRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/demo-requests")
public class PublicDemoRequestController {

    private final DemoRequestService demoRequests;
    private final DemoRequestCommunicationService communications;

    public PublicDemoRequestController(
            DemoRequestService demoRequests,
            DemoRequestCommunicationService communications) {
        this.demoRequests = demoRequests;
        this.communications = communications;
    }

    @PostMapping
    public ResponseEntity<?> submit(@RequestBody(required = false) Map<String, Object> body)
            throws Exception {
        return ResponseEntity.accepted().body(demoRequests.submit(body));
    }

    @PostMapping("/{requestId}/verify")
    public ResponseEntity<?> verify(
            @PathVariable String requestId,
            @RequestBody(required = false) Map<String, Object> body)
            throws Exception {
        Map<String, Object> verification = demoRequests.verify(requestId, body);
        Map<String, Object> result = new LinkedHashMap<>(verification);

        if (!Boolean.TRUE.equals(verification.get("alreadyVerified"))) {
            Map<String, Object> confirmation = communications.sendVerifiedConfirmation(requestId);
            result.put("confirmationEmailSent", Boolean.TRUE.equals(confirmation.get("sent")));
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{requestId}/resend")
    public ResponseEntity<?> resend(@PathVariable String requestId)
            throws Exception {
        return ResponseEntity.accepted().body(demoRequests.resendVerification(requestId));
    }
}
