package com.pickuppass.android.data.repository

import com.pickuppass.android.data.model.AcademicStructureResponse
import com.pickuppass.android.data.model.PickupGateItem
import com.pickuppass.android.data.model.TenantEntitlementsResponse
import com.pickuppass.android.data.remote.PickupPassApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TeacherOperationsRepository @Inject constructor(
    private val api: PickupPassApi
) {
    suspend fun getPickupGates(): ApiResult<List<PickupGateItem>> =
        try {
            val response =
                api.getActivePickupGates()

            val body =
                response.body()

            if (
                response.isSuccessful &&
                body != null
            ) {
                ApiResult.Success(
                    body.gates
                )
            } else {
                ApiResult.Failure(
                    parseError(
                        response
                            .errorBody()
                            ?.string()
                    )
                        ?: "Could not load pickup gates"
                )
            }
        } catch (error: Exception) {
            ApiResult.Failure(
                error.message
                    ?: "Network error"
            )
        }

    suspend fun getAcademicStructure():
        ApiResult<AcademicStructureResponse> =
        try {
            val response =
                api.getAcademicStructure()

            val body =
                response.body()

            if (
                response.isSuccessful &&
                body != null
            ) {
                ApiResult.Success(body)
            } else {
                ApiResult.Failure(
                    parseError(
                        response
                            .errorBody()
                            ?.string()
                    )
                        ?: "Could not load academic structure"
                )
            }
        } catch (error: Exception) {
            ApiResult.Failure(
                error.message
                    ?: "Network error"
            )
        }

    suspend fun getTenantEntitlements():
        ApiResult<TenantEntitlementsResponse> =
        try {
            val response =
                api.getTenantEntitlements()

            val body =
                response.body()

            if (
                response.isSuccessful &&
                body != null
            ) {
                ApiResult.Success(body)
            } else {
                ApiResult.Failure(
                    parseError(
                        response
                            .errorBody()
                            ?.string()
                    )
                        ?: "Could not load school feature availability"
                )
            }
        } catch (error: Exception) {
            ApiResult.Failure(
                error.message
                    ?: "Network error"
            )
        }

    private fun parseError(
        raw: String?
    ): String? {
        if (
            raw.isNullOrBlank()
        ) {
            return null
        }

        return Regex(
            "\"error\"\\s*:\\s*\"([^\"]*)\""
        )
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
    }
}
