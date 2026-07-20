package com.pickuppass.android.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import com.pickuppass.android.util.ImageCompressor
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deliberately does NOT use Firebase/Cloud Storage. As of Feb 3, 2026,
 * Cloud Storage for Firebase requires the pay-as-you-go Blaze plan (a
 * linked billing account) even for entirely free-tier usage. To stay on
 * the free Spark plan, the compressed avatar is base64-encoded and stored
 * directly as a data URI in the user's Firestore profile document instead
 * of being uploaded to a Storage bucket. ImageCompressor already targets
 * ~50KB, so the base64-inflated result (~67KB) stays comfortably under
 * Firestore's 1MiB per-document limit.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun uploadAvatar(
        context: Context,
        uid: String,
        schoolId: String,
        imageUri: Uri
    ): Result<String> = runCatching {
        val compressedBytes = ImageCompressor.compress(context, imageUri)

        val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
        val dataUri = "data:image/jpeg;base64,$base64"

        firestore.collection("users").document(uid)
            .update("photoUrl", dataUri)
            .await()

        dataUri
    }
}
