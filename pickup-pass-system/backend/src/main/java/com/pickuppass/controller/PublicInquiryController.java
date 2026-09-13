package com.pickuppass.controller;

import com.pickuppass.dto.InquiryChatRequest;
import com.pickuppass.dto.InquiryChatResponse;
import com.pickuppass.service.AiInquiryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public pre-login PickupPass product inquiry endpoint. */
@RestController
@RequestMapping("/api/public/inquiry")
public class PublicInquiryController {

    private final AiInquiryService inquiryService;

    public PublicInquiryController(AiInquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @PostMapping("/chat")
    public ResponseEntity<InquiryChatResponse> chat(
            @RequestBody(required = false) InquiryChatRequest request,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(
                inquiryService.answer(request, servletRequest));
    }
}
