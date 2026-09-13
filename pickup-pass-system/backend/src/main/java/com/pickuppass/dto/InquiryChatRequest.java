package com.pickuppass.dto;

import java.util.List;

/**
 * Public PickupPass product-inquiry chat request.
 *
 * The backend performs explicit limits instead of trusting browser validation,
 * because this endpoint is intentionally available before authentication.
 */
public record InquiryChatRequest(
        String message,
        List<Turn> history) {

    public record Turn(
            String role,
            String content) {
    }
}
