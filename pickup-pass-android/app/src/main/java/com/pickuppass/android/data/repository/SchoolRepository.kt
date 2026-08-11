package com.pickuppass.android.data.repository

import android.content.Context
import android.net.Uri
import com.pickuppass.android.data.remote.PickupPassApi
import com.pickuppass.android.data.model.TenantEntitlementsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_LOGO_UPLOAD_BYTES = 2L * 1024 * 1024 // matches the backend's raw-upload cap

@Singleton
class SchoolRepository @Inject constructor(
    private val api: PickupPassApi
) {
    /**
     * Uploads a picked image as the school's logo. Sent as raw multipart
     * bytes — no client-side resizing here, since the backend already
     * resizes/re-encodes server-side (SchoolLogoService), so duplicating
     * that logic on-device would just be redundant work.
     */
    suspend fun uploadLogo(context: Context, uri: Uri): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }

            if (bytes == null) {
                return@withContext ApiResult.Failure("Could not read the selected image")
            }
            if (bytes.size > MAX_LOGO_UPLOAD_BYTES) {
                return@withContext ApiResult.Failure("Image is too large — please choose one under 2MB")
            }

            val extension = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }

            val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "logo.$extension", requestBody)

            val response = api.uploadSchoolLogo(part)
            val body = response.body()

            if (response.isSuccessful && body?.logoUrl != null) {
                ApiResult.Success(body.logoUrl)
            } else {
                ApiResult.Failure(body?.error ?: "Upload failed — please try a different image")
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun getEntitlements(): ApiResult<TenantEntitlementsResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getTenantEntitlements()
            val body = response.body()
            if (response.isSuccessful && body != null) ApiResult.Success(body)
            else ApiResult.Failure("Could not load plan and feature access")
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }
}
