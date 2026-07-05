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
-keepclassmembers class * {
    static <fields>;
    *** Companion;
}
-keepclasseswithmembers class * {
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
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# --- Hilt / Dagger ---
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep class * extends androidx.lifecycle.ViewModel
-keepclassmembers class * {
    @javax.inject.* <fields>;
    @dagger.hilt.* <fields>;
}
-dontwarn dagger.hilt.**

# --- Kotlin Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- Firebase AI ---
-keep class com.google.firebase.ai.** { *; }
-dontwarn com.google.firebase.ai.**

# --- SQLCipher ---
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# --- WorkManager ---
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keepclassmembers class * {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
