# --- youtubedl-android engine -------------------------------------------------
-keep class com.yausername.** { *; }
-keepclassmembers class com.yausername.youtubedl_android.mapper.** { *; }

# --- Jackson (used by the engine to parse yt-dlp JSON) ------------------------
-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* <fields>;
    @com.fasterxml.jackson.annotation.* <methods>;
}
-dontwarn com.fasterxml.jackson.**
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry
-dontwarn java.beans.**

# --- commons-io / commons-compress --------------------------------------------
# ExtraFieldUtils instantiates these by reflection while unzipping the bundled python
# payload; without this R8 turns them abstract and engine setup dies with
# "class ... is not a concrete class".
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
