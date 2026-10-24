# kotlinx.serialization (routes de navigation typées)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.sokoul.iptv.**$$serializer { *; }
-keepclassmembers class com.sokoul.iptv.** { *** Companion; }
-keepclasseswithmembers class com.sokoul.iptv.** { kotlinx.serialization.KSerializer serializer(...); }

# Décodeur FFmpeg (JNI)
-keep class androidx.media3.decoder.ffmpeg.** { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
