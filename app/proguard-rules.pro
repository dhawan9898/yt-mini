# NexusStream Proguard Rules
# AndroidX Media3 rules
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-keep class androidx.compose.material3.** { *; }

# ---- NewPipeExtractor (reflection, Rhino JS engine, generated tables) ----
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**
-dontwarn org.mozilla.javascript.JavaToJSONConverters

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- WorkManager workers are created by reflection ----
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# ---- Google Cast options provider is referenced from the manifest ----
-keep class com.nexusstream.app.cast.CastOptionsProvider { *; }

# ---- Keep the JSON-backed data classes' constructors (named arguments are used in code only) ----
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
