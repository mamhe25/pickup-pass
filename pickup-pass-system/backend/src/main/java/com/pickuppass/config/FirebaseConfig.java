package com.pickuppass.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.auth.FirebaseAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Deliberately does NOT configure Firebase/Cloud Storage. As of Feb 3, 2026,
 * Cloud Storage for Firebase requires the pay-as-you-go Blaze plan (a linked
 * billing account) even for entirely free-tier usage. To stay on the free
 * Spark plan, image uploads (school logos, parent avatars) are stored as
 * base64 data URIs directly in Firestore documents instead — see
 * SchoolLogoService. Firestore itself has no such billing requirement.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    /**
     * Left blank (the default) when running on Cloud Run / GCE / any
     * environment with an attached Google service account — in that case
     * Application Default Credentials are used instead, and no JSON key
     * file is needed at all. Set this only for local development or hosts
     * without an attached service account.
     */
    @Value("${firebase.credentials.path:}")
    private String credentialsPath;

    @Value("${firebase.project-id}")
    private String projectId;

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        GoogleCredentials credentials;
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            log.info("Initializing Firebase using credentials file: {}", credentialsPath);
            try (FileInputStream serviceAccount = new FileInputStream(credentialsPath)) {
                credentials = GoogleCredentials.fromStream(serviceAccount);
            }
        } else {
            log.info("FIREBASE_CREDENTIALS_PATH not set — using Application Default Credentials " +
                    "(expected on Cloud Run / GCE with an attached service account)");
            credentials = GoogleCredentials.getApplicationDefault();
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId(projectId)
                .build();
        return FirebaseApp.initializeApp(options);
    }

    @Bean
    public Firestore firestore(FirebaseApp app) {
        return FirestoreClient.getFirestore(app);
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp app) {
        return FirebaseAuth.getInstance(app);
    }
}
