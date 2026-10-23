# Room entities / converters and enums are read through generated code; keep them intact.
-keep class com.local.salarytracker.data.** { *; }
-keep class com.local.salarytracker.model.** { *; }
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
# Components are referenced from the manifest (kept automatically); WorkManager worker is created by reflection.
-keep class com.local.salarytracker.work.ReminderWorker { *; }
-keepattributes *Annotation*, SourceFile, LineNumberTable
