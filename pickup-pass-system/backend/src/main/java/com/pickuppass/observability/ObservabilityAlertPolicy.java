package com.pickuppass.observability;

/** Pure threshold decisions shared by runtime evaluation and deterministic tests. */
public final class ObservabilityAlertPolicy {
    private ObservabilityAlertPolicy() { }

    public static boolean rateExceeded(long requests,
                                       int minimumRequests,
                                       double observedPercent,
                                       double thresholdPercent) {
        return requests >= minimumRequests && observedPercent >= thresholdPercent;
    }

    public static boolean memoryExceeded(long maxBytes, int usedPercent, int thresholdPercent) {
        return maxBytes > 0 && usedPercent >= thresholdPercent;
    }

    public static boolean consecutiveFailuresExceeded(int failures, int threshold) {
        return failures >= threshold;
    }
}
