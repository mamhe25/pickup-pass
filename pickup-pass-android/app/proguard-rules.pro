# Keep Retrofit/Gson model classes (reflection-based serialization)
-keep class com.pickuppass.android.data.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
