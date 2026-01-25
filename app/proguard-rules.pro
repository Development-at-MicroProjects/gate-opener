# Gate Opener ProGuard Rules

# Keep reflection-based call rejection
-keep class com.android.internal.telephony.** { *; }
-keep class android.telephony.** { *; }

# Keep our classes
-keep class com.microprojects.gateopener.** { *; }
