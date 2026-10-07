# --- youtubedl-android engine -------------------------------------------------
# Small, and its mapper classes (VideoInfo, VideoFormat…) are filled in by Jackson through
# reflection, so they keep their fields and names.
-keep class com.yausername.** { *; }

# --- Jackson (the engine's JSON parser) ---------------------------------------
# Jackson reflects over the mapper classes kept above, reading their annotations and generic
# signatures (both preserved by proguard-android-optimize.txt). Of Jackson itself only the
# names must survive: it looks a few of its own optional helpers up by name (Java7Support,
# OptionalHandlerFactory) and copes when they are absent. Everything the app never reaches is
# shrunk away like any other library code.
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry
-dontwarn java.beans.**

# --- commons-compress ---------------------------------------------------------
# ExtraFieldUtils creates zip extra fields with Class.newInstance() while unpacking the bundled
# python payload. Without their no-arg constructors R8 turns those classes abstract and engine
# setup dies with "class ... is not a concrete class". Nothing else in commons-compress or
# commons-io is reached by reflection.
-keep class * implements org.apache.commons.compress.archivers.zip.ZipExtraField { <init>(); }
-dontwarn org.apache.commons.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
