# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# Keep Room database entities and DAOs
-keep class com.fsstructure.creator.data.** { *; }

# Keep Kotlin Metadata (Required for modern Kotlin libraries and reflection)
-keep class kotlin.Metadata { *; }

# OkHttp (usually handled by consumer rules, but keeping for safety)
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep standard Android and Compose lifecycle classes
-keep class androidx.lifecycle.** { *; }
-keep class androidx.compose.** { *; }

# If using any JSON parsing manually (if not fully using Kotlinx Serialization which would need its own rules)
# But OkHttp's MediaType and RequestBody are generally safe.