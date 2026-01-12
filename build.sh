#!/bin/bash
# ArchDroid Build Script

# Change to project directory
cd /workspace/archdroid

# Set environment variables
export JAVA_HOME=/workspace/jdk-17.0.9+9
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=/workspace/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

echo "Building ArchDroid..."
echo "Java: $(java -version 2>&1 | head -1)"
echo "Android SDK: $ANDROID_HOME"

# Run build
/tmp/gradle-8.7/bin/gradle clean assembleDebug --no-daemon --stacktrace 2>&1
