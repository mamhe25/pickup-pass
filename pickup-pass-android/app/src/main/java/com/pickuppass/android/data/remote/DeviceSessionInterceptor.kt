package com.pickuppass.android.data.remote

import com.pickuppass.android.session.DeviceIdentity
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceSessionInterceptor @Inject constructor(
    private val identity: DeviceIdentity
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-Device-Id", identity.deviceId)
            .header("X-Device-Name", identity.deviceName)
            .build()
        return chain.proceed(request)
    }
}
