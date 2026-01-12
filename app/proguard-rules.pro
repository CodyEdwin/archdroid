# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ArchDroid ProGuard Rules

# Keep application classes
-keep class com.archdroid.MainActivity { *; }
-keep class com.archdroid.SettingsActivity { *; }

# Keep terminal classes
-keep class com.archdroid.terminal.** { *; }
-keep class com.archdroid.bootstrap.** { *; }

# Keep model classes
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Kotlin specific (for any Kotlin code that might be added)
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**

# Material Components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve annotations
-keepattributes *Annotation*

# Preserve line number table
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
