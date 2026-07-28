package com.pickuppass.service;

import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.pickuppass.exception.ConflictException;
import com.pickuppass.util.NameFormatter;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Creates staff accounts — teacher, school_admin, or master_admin — via the
 * Admin SDK. Kept separate from GuardianProvisioningService (which only
 * ever creates 'parent'-role accounts reachable by teachers/parents) so the
 * two trust boundaries can't accidentally blur: nothing that creates staff
 * accounts is reachable except by an already-authenticated master_admin (or,
 * for teachers specifically, a school_admin), or by the one-time bootstrap
 * endpoint.
 */
@Service
public class StaffProvisioningService {

    private final Firestore firestore;
    private final EmailService emailService;

    public StaffProvisioningService(Firestore firestore, EmailService emailService) {
        this.firestore = firestore;
        this.emailService = emailService;
    }

    public static class StaffCreationResult {
        private final String uid;
        private final boolean emailSent;

        public StaffCreationResult(String uid, boolean emailSent) {
            this.uid = uid;
            this.emailSent = emailSent;
        }

        public String getUid() { return uid; }
        public boolean isEmailSent() { return emailSent; }
    }

    /**
     * @param schoolId null only for role == "master_admin" (a global role,
     *                 not scoped to any one school)
     * @throws ConflictException if a Firebase Auth account with this email
     *         already exists.
     */
    public StaffCreationResult createStaffAccount(
            String email, String lastName, String firstName, String middleInitial, String suffix,
            String role, String schoolId)
            throws FirebaseAuthException, java.util.concurrent.ExecutionException, InterruptedException {

        if (staffAccountExists(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        String displayName = NameFormatter.format(lastName, firstName, middleInitial, suffix);

        UserRecord created = FirebaseAuth.getInstance().createUser(
                new UserRecord.CreateRequest()
                        .setEmail(email)
                        .setEmailVerified(false)
                        .setPassword(generateTempPassword())
                        .setDisplayName(displayName)
        );

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        if (schoolId != null) {
            claims.put("schoolId", schoolId);
        }
        FirebaseAuth.getInstance().setCustomUserClaims(created.getUid(), claims);

        Map<String, Object> profile = new HashMap<>();
        profile.put("role", role);
        profile.put("email", email);
        profile.put("displayName", displayName);
        profile.put("lastName", lastName.trim());
        profile.put("firstName", firstName.trim());
        profile.put("middleInitial", middleInitial != null ? middleInitial.trim() : "");
        profile.put("suffix", suffix != null ? suffix.trim() : "");
        profile.put("isActive", true);
        profile.put("createdAt", FieldValue.serverTimestamp());
        if (schoolId != null) {
            profile.put("schoolId", schoolId);
        }
        firestore.collection("users").document(created.getUid()).set(profile).get();

        String resetLink = FirebaseAuth.getInstance().generatePasswordResetLink(email);
        boolean emailSent = emailService.sendStaffInvite(email, displayName, role, resetLink);

        return new StaffCreationResult(created.getUid(), emailSent);
    }

    /** See GuardianProvisioningService.tryGetUserByEmail for why only USER_NOT_FOUND is treated as "doesn't exist". */
    private boolean staffAccountExists(String email) throws FirebaseAuthException {
        try {
            FirebaseAuth.getInstance().getUserByEmail(email);
            return true;
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }

    private String generateTempPassword() {
        return UUID.randomUUID().toString().substring(0, 12);
    }
}
