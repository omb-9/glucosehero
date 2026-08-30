# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.omb9.glucosehero.**$$serializer { *; }
-keepclassmembers class com.omb9.glucosehero.** { *** Companion; }
-keepclasseswithmembers class com.omb9.glucosehero.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes Signature, Exceptions
