package com.pickuppass.dto;

import java.util.Date;

public class PickupTokenResponse {

    private final String qrToken;
    private final Date expiresAt;
    private final Date dismissalDeadline;
    private final boolean testMode;
    private final String operationalMode;

    public PickupTokenResponse(
            String qrToken,
            Date expiresAt,
            Date dismissalDeadline,
            boolean testMode,
            String operationalMode) {
        this.qrToken = qrToken;
        this.expiresAt = expiresAt;
        this.dismissalDeadline = dismissalDeadline;
        this.testMode = testMode;
        this.operationalMode = operationalMode;
    }

    public String getQrToken() { return qrToken; }
    public Date getExpiresAt() { return expiresAt; }
    public Date getDismissalDeadline() { return dismissalDeadline; }
    public boolean isTestMode() { return testMode; }
    public String getOperationalMode() { return operationalMode; }
}
