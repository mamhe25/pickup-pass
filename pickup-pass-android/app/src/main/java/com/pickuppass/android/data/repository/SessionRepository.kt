package com.pickuppass.android.data.repository

import com.pickuppass.android.data.model.DeviceSessionItem
import com.pickuppass.android.data.remote.PickupPassApi
import com.pickuppass.android.session.DeviceIdentity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val api: PickupPassApi,
    private val identity: DeviceIdentity
) {
    val currentDeviceId: String get() = identity.deviceId

    suspend fun listDevices(): ApiResult<List<DeviceSessionItem>> = try {
        val response = api.listDeviceSessions()
        val body = response.body()
        if (response.isSuccessful && body != null) ApiResult.Success(body.devices)
        else ApiResult.Failure("Could not load signed-in devices")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun revoke(deviceId: String): ApiResult<Unit> = try {
        val response = api.revokeDeviceSession(deviceId)
        if (response.isSuccessful) ApiResult.Success(Unit)
        else ApiResult.Failure("Could not sign out that device")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }

    suspend fun revokeOthers(): ApiResult<Int> = try {
        val response = api.revokeOtherDeviceSessions()
        val body = response.body()
        if (response.isSuccessful) ApiResult.Success(body?.revokedCount ?: 0)
        else ApiResult.Failure("Could not sign out other devices")
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }
}
