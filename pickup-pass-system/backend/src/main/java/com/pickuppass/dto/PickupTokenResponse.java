package com.pickuppass.dto;

import java.util.Date;

public class PickupTokenResponse {

    private final String qrToken;
    private final Date expiresAt;
    private final Date dismissalDeadline;

    public PickupTokenResponse(String qrToken, Date expiresAt, Date dismissalDeadline) {
        this.qrToken = qrToken;
        this.expiresAt = expiresAt;
        this.dismissalDeadline = dismissalDeadline;
    }

    public String getQrToken() { return qrToken; }
    public Date getExpiresAt() { return expiresAt; }
    public Date getDismissalDeadline() { return dismissalDeadline; }
}
