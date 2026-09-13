package com.pickuppass.controller;

import com.pickuppass.service.DemoRequestService;
import org.springframework.http.ResponseEntity;
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
        return ResponseEntity.status(201).body(demoRequests.submit(body));
    }
}
