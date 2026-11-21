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

# Preserve stack trace line numbers and reflection/Room/Kotlin generic metadata
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Room database, entities, DAOs, and domain models
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public abstract <methods>;
}
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.PrimaryKey *;
    @androidx.room.ColumnInfo *;
    @androidx.room.Embedded *;
    @androidx.room.Relation *;
    @androidx.room.TypeConverter *;
}
-keepclassmembers class com.example.data.** { *; }
-keepclassmembers class com.example.domain.** { *; }
-keep class com.example.data.** { *; }
-keep class com.example.domain.** { *; }

# Manifest-declared FileProvider, ViewModel constructors, and Serializable / Enum banking models
-keep class androidx.core.content.FileProvider { *; }
-keepclassmembers class com.example.ui.MainViewModel {
    public <init>(android.app.Application);
    public <init>(android.app.Application, androidx.lifecycle.SavedStateHandle);
}
-keep class com.example.util.ParsedBankTransaction { *; }
-keep class com.example.util.StatementParseSummary { *; }
-keep class com.example.util.BankType { *; }
-keep class com.example.util.BankTransactionType { *; }
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Strip verbose/debug logs and Throwable.printStackTrace() in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
    public void printStackTrace(java.io.PrintStream);
    public void printStackTrace(java.io.PrintWriter);
}

