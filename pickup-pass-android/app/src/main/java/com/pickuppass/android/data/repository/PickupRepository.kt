package com.pickuppass.android.data.repository

import com.pickuppass.android.data.model.ApproveResponse
import com.pickuppass.android.data.model.GenerateTokenRequest
import com.pickuppass.android.data.model.ManualOverrideRequest
import com.pickuppass.android.data.model.ManualOverrideResponse
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.data.model.PickupTokenResponse
import com.pickuppass.android.data.model.VerifyRequest
import com.pickuppass.android.data.model.VerifyResponse
import com.pickuppass.android.data.remote.PickupPassApi
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class PickupRepository @Inject constructor(
    private val api: PickupPassApi
) {
    suspend fun generateToken(studentId: String): ApiResult<PickupTokenResponse> {
        return try {
            val response = api.generateToken(GenerateTokenRequest(studentId))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body)
            } else {
                ApiResult.Failure(
                    parseError(response.errorBody()?.string())
                        ?: "Could not generate pass"
                )
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun verify(qrToken: String): ApiResult<VerifyResponse> {
        return try {
            val response = api.verifyPickup(VerifyRequest(qrToken))
            val body = response.body()
            when {
                response.isSuccessful && body != null && body.valid -> {
                    ApiResult.Success(body)
                }

                body != null -> {
                    ApiResult.Failure(body.reason ?: "QR code not valid")
                }

                else -> {
                    ApiResult.Failure(
                        parseError(response.errorBody()?.string())
                            ?: "Could not verify QR code"
                    )
                }
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun getActivePickupGates(): ApiResult<List<PickupGateItem>> {
        return try {
            val response = api.getActivePickupGates()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                ApiResult.Success(body.gates)
            } else {
                ApiResult.Failure(
                    parseError(response.errorBody()?.string())
                        ?: "Could not load pickup gates"
                )
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun approve(
        qrToken: String,
        pickupGateId: String? = null
    ): ApiResult<ApproveResponse> {
        return try {
            val response = api.approvePickup(
                PickupIdempotencyKeys.approval(qrToken),
                pickupGateId,
                VerifyRequest(qrToken)
            )
            val body = response.body()
            if (response.isSuccessful && body?.status == "release_approved") {
                ApiResult.Success(body)
            } else {
                ApiResult.Failure(
                    body?.reason
                        ?: parseError(response.errorBody()?.string())
                        ?: "Approval failed"
                )
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    suspend fun manualOverride(
        studentId: String,
        guardianUid: String,
        reason: String,
        pickupGateId: String? = null
    ): ApiResult<ManualOverrideResponse> {
        return try {
            val response = api.manualOverride(
                PickupIdempotencyKeys.manualOverride(
                    studentId,
                    guardianUid,
                    reason,
                    pickupGateId
                ),
                pickupGateId,
                ManualOverrideRequest(studentId, guardianUid, reason.trim())
            )
            val body = response.body()
            if (response.isSuccessful && body?.status == "release_approved") {
                ApiResult.Success(body)
            } else {
                ApiResult.Failure(
                    body?.error
                        ?: parseError(response.errorBody()?.string())
                        ?: "Manual pickup approval failed"
                )
            }
        } catch (e: Exception) {
            ApiResult.Failure(e.message ?: "Network error")
        }
    }

    private fun parseError(raw: String?): String? {
        val content = raw?.trim().orEmpty()
        if (content.isEmpty()) return null

        return runCatching {
            val json = JSONObject(content)
            listOf("reason", "error", "message")
                .firstNotNullOfOrNull { key ->
                    json.optString(key)
                        .trim()
                        .takeIf { it.isNotEmpty() }
                }
        }.getOrNull()
    }
}
