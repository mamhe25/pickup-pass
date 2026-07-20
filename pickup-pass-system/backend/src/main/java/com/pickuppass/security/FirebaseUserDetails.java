package com.pickuppass.security;

/**
 * Lightweight principal built from a verified Firebase ID token's claims.
 * schoolId and role are read from Firebase custom claims (set server-side
 * when a user account is created/promoted), never trusted from client input.
 */
public class FirebaseUserDetails {

    private final String uid;
    private final String email;
    private final String schoolId;
    private final String role;

    public FirebaseUserDetails(String uid, String email, String schoolId, String role) {
        this.uid = uid;
        this.email = email;
        this.schoolId = schoolId;
        this.role = role;
    }

    public String getUid() { return uid; }
    public String getEmail() { return email; }
    public String getSchoolId() { return schoolId; }
    public String getRole() { return role; }
}
