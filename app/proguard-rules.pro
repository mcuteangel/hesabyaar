# ProGuard rules for Hesabyar
# Keep line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# --- Moshi ---
-keep @com.squareup.moshi.JsonClass class *
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep @com.squareup.moshi.JsonAdapter class *
-dontwarn javax.annotation.**
-keepattributes *Annotation*

# --- Moshi Codegen (KSP) ---
-keep class **JsonAdapter { *; }
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    static <fields>;
    *** Companion;
}
-keepclasseswithmembers @com.squareup.moshi.JsonClass class * {
    *** Companion;
}

# --- Retrofit ---
-keepattributes Signature
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# --- OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class org.conscrypt.** { *; }
-keep class org.bouncycastle.** { *; }

# --- Hilt / Dagger ---
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keepclassmembers class * {
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <init>(...);
    @dagger.hilt.android.lifecycle.HiltViewModel <fields>;
}
-dontwarn dagger.hilt.**

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- SQLCipher ---
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# --- WorkManager ---
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keepclassmembers class * {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- JNA (used by UniFFI Rust bindings) ---
# The release build minifies code. Without these rules R8 renames
# com.sun.jna.Pointer.peer, and JNA native init fails at startup with
# UnsatisfiedLinkError, crashing the release APK on launch.
# JNA's Native$AWT references desktop java.awt classes.
# Android does not provide java.awt, so R8 fails on the missing classes.
# This rule tells R8 to ignore those references.
-dontwarn java.awt.*
-keep class com.sun.jna.** { *; }
# UniFFI marks Structure subclasses with @Structure.FieldOrder.
# JNA reads this annotation with reflection at runtime.
# R8 strips the annotation from optimized classes.
# Keep the subclasses entirely so the annotation survives.
-keep class * extends com.sun.jna.Structure { *; }
# The bindings are installed with the app package, so the interface
# is io.github.mojri.hesabyar.rust.UniffiLib, not uniffi.hesabyar_core.
# JNA builds a proxy for the interface at runtime.
# The proxy resolves native symbols from the Java method names.
# R8 must not rename the interface or its methods.
-keep interface io.github.mojri.hesabyar.rust.UniffiLib { *; }
