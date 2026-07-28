package com.pickuppass.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.pickuppass.exception.ForbiddenException;
import com.pickuppass.util.NameFormatter;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Creates (or resolves an existing) parent/guardian Firebase Auth account
 * entirely via the Admin SDK. This never touches the caller's own client-side
 * auth session — safe to call from a teacher's or a parent's authenticated
 * request without signing either of them out.
 */
@Service
public class GuardianProvisioningService {

    private final Firestore firestore;
    private final EmailService emailService;

    public GuardianProvisioningService(Firestore firestore, EmailService emailService) {
        this.firestore = firestore;
        this.emailService = emailService;
    }

    public static class ProvisionResult {
        private final String uid;
        private final boolean newlyCreated;
        private final boolean emailSent;

        public ProvisionResult(String uid, boolean newlyCreated, boolean emailSent) {
            this.uid = uid;
            this.newlyCreated = newlyCreated;
            this.emailSent = emailSent;
        }

        public String getUid() { return uid; }
        public boolean isNewlyCreated() { return newlyCreated; }
        /** Always true for an already-existing account (no email is sent in that case). */
        public boolean isEmailSent() { return emailSent; }
    }

    /**
     * @param lastName, firstName, middleInitial, suffix — structured name
     *        parts, formatted into "Lastname, Firstname M. Suffix" via
     *        NameFormatter and stored as displayName. Only used when
     *        actually creating a new account; ignored for an existing one
     *        (their name was already set whenever their account was first
     *        created — this call doesn't rename them).
     */
    public ProvisionResult provisionGuardianAccount(
            String email, String lastName, String firstName, String middleInitial, String suffix, String schoolId)
            throws FirebaseAuthException, ExecutionException, InterruptedException {

        UserRecord existing = tryGetUserByEmail(email);

        if (existing != null) {
            // Guard against cross-tenant linking: an existing account must
            // already belong to this same school.
            DocumentSnapshot existingProfile = firestore.collection("users")
                    .document(existing.getUid()).get().get();
            if (existingProfile.exists() && !schoolId.equals(existingProfile.getString("schoolId"))) {
                throw new ForbiddenException("This email is already registered at a different school");
            }
            return new ProvisionResult(existing.getUid(), false, true);
        }

        String displayName = NameFormatter.format(lastName, firstName, middleInitial, suffix);

        UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                .setEmail(email)
                .setEmailVerified(false)
                .setPassword(generateTempPassword())
                .setDisplayName(displayName);
        UserRecord created = FirebaseAuth.getInstance().createUser(createRequest);

        Map<String, Object> claims = Map.of("role", "parent", "schoolId", schoolId);
        FirebaseAuth.getInstance().setCustomUserClaims(created.getUid(), claims);

        Map<String, Object> profile = new HashMap<>();
        profile.put("schoolId", schoolId);
        profile.put("role", "parent");
        profile.put("email", email);
        profile.put("displayName", displayName);
        profile.put("lastName", lastName.trim());
        profile.put("firstName", firstName.trim());
        profile.put("middleInitial", middleInitial != null ? middleInitial.trim() : "");
        profile.put("suffix", suffix != null ? suffix.trim() : "");
        profile.put("isActive", true);
        profile.put("createdAt", FieldValue.serverTimestamp());
        firestore.collection("users").document(created.getUid()).set(profile).get();

        String link = FirebaseAuth.getInstance().generatePasswordResetLink(email);
        boolean emailSent = emailService.sendParentInvite(email, displayName, link);

        return new ProvisionResult(created.getUid(), true, emailSent);
    }

    /**
     * Returns null only when the lookup fails specifically because the user
     * doesn't exist yet (AuthErrorCode.USER_NOT_FOUND). Any OTHER failure
     * (network blip, wrong project, quota, etc.) is rethrown rather than
     * silently treated as "doesn't exist" — the previous version caught
     * FirebaseAuthException broadly here and fell through to createUser()
     * regardless of the actual cause, which meant a transient lookup
     * failure could lead straight into an unhandled EMAIL_ALREADY_EXISTS
     * from createUser() a moment later.
     */
    private UserRecord tryGetUserByEmail(String email) throws FirebaseAuthException {
        try {
            return FirebaseAuth.getInstance().getUserByEmail(email);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    private String generateTempPassword() {
        return UUID.randomUUID().toString().substring(0, 12);
    }
}
