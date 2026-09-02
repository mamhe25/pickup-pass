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
 * Deliberately does NOT use Firebase/Cloud Storage. The compressed avatar is
 * base64 encoded and stored directly as a data URI in the user's Firestore
 * profile document.
 *
 * `schoolId` remains in this stable repository contract because existing
 * callers supply it and it provides useful tenancy context for a future
 * storage/audit implementation. The current Firestore write is user-document
 * scoped, so it is intentionally unused here.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun uploadAvatar(
        context: Context,
        uid: String,
        schoolId: String,
        imageUri: Uri
    ): Result<String> = runCatching {
        val compressedBytes = ImageCompressor.compress(context, imageUri)
        val base64 = Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
        val dataUri = "data:image/jpeg;base64,$base64"

        firestore.collection("users")
            .document(uid)
            .update("photoUrl", dataUri)
            .await()

        dataUri
    }
}
