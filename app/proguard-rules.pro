# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep log tags.
-keepclasseswithmembers,allowoptimization,allowshrinking class com.chiller3.bcr.** {
    static final java.lang.String TAG;
}
-keep,allowoptimization,allowshrinking class com.chiller3.bcr.RecorderThread {
}

# We construct TreeDocumentFile via reflection in DocumentFileExtensions
# to speed up SAF performance when doing path lookups.
-keepclassmembers class androidx.documentfile.provider.TreeDocumentFile {
    <init>(androidx.documentfile.provider.DocumentFile, android.content.Context, android.net.Uri);
}

# ChipGroupCentered accesses this via reflection.
-keepclassmembers class com.google.android.material.internal.FlowLayout {
    private int rowCount;
}

# Keep standalone CLI utilities
-keep class com.chiller3.bcr.standalone.* {
    void main(java.lang.String[]);
}
