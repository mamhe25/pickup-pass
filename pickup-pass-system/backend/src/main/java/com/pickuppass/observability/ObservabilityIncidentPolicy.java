package com.pickuppass.observability;

/** Determines durable incident state transitions without performing database writes. */
public final class ObservabilityIncidentPolicy {
    private ObservabilityIncidentPolicy() { }

    public enum Action {
        NONE,
        OPEN,
        RESOLVE
    }

    public static Action decide(boolean exists, String currentStatus, boolean conditionActive) {
        String status = currentStatus == null || currentStatus.isBlank() ? "resolved" : currentStatus;
        boolean activeIncident = exists && ("open".equals(status) || "acknowledged".equals(status));

        if (conditionActive) {
            return activeIncident ? Action.NONE : Action.OPEN;
        }
        return activeIncident ? Action.RESOLVE : Action.NONE;
    }
}
