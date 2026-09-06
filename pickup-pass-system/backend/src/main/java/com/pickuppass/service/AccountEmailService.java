package com.pickuppass.service;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class AccountEmailService {
    private final Firestore firestore;
    private final FirebaseAuth auth;

    public AccountEmailService(Firestore firestore, FirebaseAuth auth) {
        this.firestore = firestore;
        this.auth = auth;
    }

    /** Firebase applies pending email changes only after verification. Never trust a client email. */
    public void synchronize(String uid) throws Exception {
        var reference = firestore.collection("users").document(uid);
        firestore.runTransaction(transaction -> {
            var profile = transaction.get(reference).get();
            if (!profile.exists()) return null;
            // Read the authoritative value on every transaction attempt, not a stale ID-token claim.
            String email = auth.getUser(uid).getEmail();
            if (email != null && !email.isBlank() && !email.equals(profile.getString("email"))) {
                transaction.update(reference, Map.of("email", email));
            }
            return null;
        }).get();
    }
}
