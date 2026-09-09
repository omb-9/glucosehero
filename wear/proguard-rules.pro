# Wear OS companion. Minify is off for the initial module; keep rules ready
# for a later size pass.
-keepattributes *Annotation*, InnerClasses
-keep class com.google.android.gms.wearable.** { *; }
