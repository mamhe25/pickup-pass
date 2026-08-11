package com.pickuppass.android.telemetry

import com.pickuppass.android.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/** Adds a request correlation ID and records actionable production network failures. */
class NetworkTelemetryInterceptor @Inject constructor(
    private val telemetry: AppTelemetry
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestId = UUID.randomUUID().toString()
        val original = chain.request()
        val request = original.newBuilder()
            .header("X-Request-ID", requestId)
            .header("X-Client-Version", BuildConfig.VERSION_NAME)
            .build()

        val started = System.nanoTime()
        return try {
            val response = chain.proceed(request)
            val durationMs = (System.nanoTime() - started) / 1_000_000
            if (durationMs >= 5_000) {
                telemetry.log("Slow API call ${request.url.encodedPath}: ${durationMs}ms")
            }
            if (response.code >= 500 || response.code == 429) {
                telemetry.recordHttpFailure(
                    request.url.encodedPath,
                    response.code,
                    response.header("X-Request-ID") ?: requestId
                )
            }
            response
        } catch (e: IOException) {
            telemetry.recordNetworkFailure(request.url.encodedPath, e, requestId)
            throw e
        }
    }
}
