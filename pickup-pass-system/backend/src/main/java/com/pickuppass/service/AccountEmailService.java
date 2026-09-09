package com.pickuppass.service;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

@Service
public class AccountEmailService {
    private final Firestore firestore;
    private final FirebaseAuth auth;
    private final AuditService audit;

    public AccountEmailService(
            Firestore firestore,
            FirebaseAuth auth,
            AuditService audit) {
        this.firestore = firestore;
        this.auth = auth;
        this.audit = audit;
    }

    /**
     * Firebase applies pending email changes only after verification.
     * Never trust a client-provided email value.
     *
     * Returns true only when the authoritative Firebase email changed the
     * stored PickupPass profile. The audit event intentionally records no raw
     * email values.
     */
    public boolean synchronize(String uid) throws Exception {
        var reference =
                firestore.collection("users").document(uid);

        EmailSyncResult result =
                firestore.runTransaction(transaction -> {
                    var profile =
                            transaction.get(reference).get();

                    if (!profile.exists()) {
                        return EmailSyncResult.unchanged();
                    }

                    String email =
                            auth.getUser(uid).getEmail();

                    String storedEmail =
                            profile.getString("email");

                    if (
                        email == null ||
                        email.isBlank() ||
                        Objects.equals(email, storedEmail)
                    ) {
                        return EmailSyncResult.unchanged();
                    }

                    transaction.update(
                            reference,
                            Map.of("email", email));

                    return new EmailSyncResult(
                            true,
                            profile.getString("schoolId"));
                }).get();

        if (result.changed()) {
            audit.recordSystem(
                    result.schoolId(),
                    "account.email_changed",
                    "user_account",
                    uid,
                    Map.of(
                            "source", "firebase_verified_email"));
        }

        return result.changed();
    }

    private record EmailSyncResult(
            boolean changed,
            String schoolId) {

        static EmailSyncResult unchanged() {
            return new EmailSyncResult(false, null);
        }
    }
}
