package com.pickuppass.controller;

import com.pickuppass.service.DemoRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/public/demo-requests")
public class PublicDemoRequestController {

    private final DemoRequestService demoRequests;

    public PublicDemoRequestController(DemoRequestService demoRequests) {
        this.demoRequests = demoRequests;
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
        return ResponseEntity.ok(demoRequests.verify(requestId, body));
    }

    @PostMapping("/{requestId}/resend")
    public ResponseEntity<?> resend(@PathVariable String requestId)
            throws Exception {
        return ResponseEntity.accepted().body(demoRequests.resendVerification(requestId));
    }
}
