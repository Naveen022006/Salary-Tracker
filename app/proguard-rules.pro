# Room entities / converters and enums are read through generated code; keep them intact.
-keep class com.local.salarytracker.data.** { *; }
-keep class com.local.salarytracker.model.** { *; }
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
# Components are referenced from the manifest (kept automatically); WorkManager worker is created by reflection.
-keep class com.local.salarytracker.work.ReminderWorker { *; }
-keepattributes *Annotation*, SourceFile, LineNumberTable

# ---- Cloud sync (supabase-kt, Ktor, kotlinx.serialization) ----
-keepattributes InnerClasses, Signature, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keep class com.local.salarytracker.cloud.SyncWorker { *; }
# @Serializable DTOs: generated serializers and companions are looked up by reflection-like code
-keep,includedescriptorclasses class com.local.salarytracker.cloud.**$$serializer { *; }
-keepclassmembers class com.local.salarytracker.cloud.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.local.salarytracker.cloud.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class kotlinx.serialization.** { *; }
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
