package com.pickuppass.security;

/**
 * Lightweight principal built exclusively from a verified Firebase ID token.
 *
 * schoolId, role and MFA state are never trusted from client input.
 */
public class FirebaseUserDetails {

    private final String uid;
    private final String email;
    private final String schoolId;
    private final String role;
    private final boolean mfaSatisfied;

    /**
     * Backward-compatible constructor used by existing unit tests and helper
     * code. A caller using this constructor is intentionally treated as a
     * first-factor-only session.
     */
    public FirebaseUserDetails(
            String uid,
            String email,
            String schoolId,
            String role) {
        this(uid, email, schoolId, role, false);
    }

    public FirebaseUserDetails(
            String uid,
            String email,
            String schoolId,
            String role,
            boolean mfaSatisfied) {
        this.uid = uid;
        this.email = email;
        this.schoolId = schoolId;
        this.role = role;
        this.mfaSatisfied = mfaSatisfied;
    }

    public String getUid() {
        return uid;
    }

    public String getEmail() {
        return email;
    }

    public String getSchoolId() {
        return schoolId;
    }

    public String getRole() {
        return role;
    }

    public boolean isMfaSatisfied() {
        return mfaSatisfied;
    }

    public boolean requiresMfa() {
        return "school_admin".equals(role)
                || "master_admin".equals(role);
    }
}
