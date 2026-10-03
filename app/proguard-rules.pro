# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Room database, entities, and data structures
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public abstract <methods>;
}
-keepclassmembers class * {
    @androidx.room.PrimaryKey *;
    @androidx.room.ColumnInfo *;
}
-keepclassmembers class com.example.data.** { *; }
-keepclassmembers class com.example.domain.** { *; }
-keep class com.example.data.** { *; }
-keep class com.example.domain.** { *; }

