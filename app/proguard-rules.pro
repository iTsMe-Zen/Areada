# Project-specific keep rules can be added here if the app grows beyond its
# current minimal EPUB/PDF-only footprint.

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep XZ/LZMA classes needed by Apache Commons Compress for 7z support
-keep class org.tukaani.xz.** { *; }
-dontwarn org.tukaani.xz.**
