# Keep FFmpegKit JNI entry points.
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.chaquo.python.** { *; }

# kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.ffmpeg.gui.**$$serializer { *; }
-keepclassmembers class com.ffmpeg.gui.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
