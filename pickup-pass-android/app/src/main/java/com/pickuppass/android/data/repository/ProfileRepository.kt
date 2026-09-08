package com.pickuppass.android.data.repository

import android.content.Context
import android.net.Uri
import com.pickuppass.android.data.model.GuardianPhotoUploadResponse
import com.pickuppass.android.data.remote.PickupPassApi
import com.pickuppass.android.util.ImageCompressor
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Guardian verification photos are server-managed.
 *
 * The Android client only prepares a compact JPEG; the backend performs
 * authoritative image/face-quality validation and writes the validated
 * profile fields. This prevents modified clients from self-asserting that a
 * random image is a valid pickup identity photo.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val api: PickupPassApi
) {
    @Suppress("UNUSED_PARAMETER")
    suspend fun uploadAvatar(
        context: Context,
        uid: String,
        schoolId: String,
        imageUri: Uri
    ): ApiResult<GuardianPhotoUploadResponse> {
        return try {
            val compressedBytes =
                ImageCompressor.compress(
                    context = context,
                    uri = imageUri,
                    targetSize = 600,
                    maxBytes = 180 * 1024
                )

            val requestBody =
                compressedBytes.toRequestBody(
                    "image/jpeg".toMediaType()
                )
            val file =
                MultipartBody.Part.createFormData(
                    "file",
                    "guardian-verification.jpg",
                    requestBody
                )

            val response =
                api.uploadGuardianVerificationPhoto(file)
            val body = response.body()

            if (
                response.isSuccessful &&
                body?.status == "verified" &&
                !body.photoUrl.isNullOrBlank()
            ) {
                ApiResult.Success(body)
            } else {
                ApiResult.Failure(
                    body?.error
                        ?: body?.message
                        ?: parseError(
                            response.errorBody()
                                ?.string()
                        )
                        ?: "Verification photo could not be accepted"
                )
            }
        } catch (e: Exception) {
            ApiResult.Failure(
                e.message ?: "Photo validation failed"
            )
        }
    }

    private fun parseError(raw: String?): String? {
        val content = raw?.trim().orEmpty()
        if (content.isBlank()) return null

        return runCatching {
            val json = JSONObject(content)
            listOf("error", "message")
                .firstNotNullOfOrNull { key ->
                    json.optString(key)
                        .trim()
                        .takeIf {
                            it.isNotBlank()
                        }
                }
        }.getOrNull()
    }
}
