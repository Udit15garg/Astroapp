# ── AstroApp ProGuard/R8 Rules ──

# ── Room Database ──
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ── Data Classes (Parcelable) ──
-keep class com.palmreader.astro.PalmReading { *; }
-keep class com.palmreader.astro.UserEntity { *; }
-keep class com.palmreader.astro.HistoryEntity { *; }
-keep class com.palmreader.astro.CreditTransactionEntity { *; }
-keep class com.palmreader.astro.api.ReadingCacheEntity { *; }

# ── Feature Engine Data Classes ──
-keep class com.palmreader.astro.TarotCard { *; }
-keep class com.palmreader.astro.ReadingItem { *; }
-keep class com.palmreader.astro.FeatureResult { *; }

# ── API Response Models ──
-keep class com.palmreader.astro.api.** { *; }

# ── BuildConfig (keep to access API key) ──
-keep class com.palmreader.astro.BuildConfig { *; }

# ── Kotlin Coroutines ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── Kotlin ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ── Google Sign-In ──
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ── Standard Android Keep Rules ──
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ── Material Components ──
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ── JSON Parsing ──
-keep class org.json.** { *; }

# ── Prevent stripping of Parcelable ──
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
