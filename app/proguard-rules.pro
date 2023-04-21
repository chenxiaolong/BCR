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

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Disable obfuscation completely for BCR. As an open source project,
# shrinking is the only goal of minification.
-dontobfuscate

# We construct TreeDocumentFile via reflection in DocumentFileExtensions
# to speed up SAF performance when doing path lookups.
-keepclassmembers class androidx.documentfile.provider.TreeDocumentFile {
    <init>(androidx.documentfile.provider.DocumentFile, android.content.Context, android.net.Uri);
}

# Keep standalone CLI utilities
-keep class com.chiller3.bcr.standalone.* {
    *;
}