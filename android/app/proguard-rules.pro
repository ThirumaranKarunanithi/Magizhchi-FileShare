# Add project specific ProGuard rules here.

# Keep model classes for Gson
-keepclassmembers class com.magizhchi.share.model.** {
    <fields>;
    <init>();
}

# Keep Retrofit interfaces
-keep interface com.magizhchi.share.network.ApiService { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Gson
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { *; }
