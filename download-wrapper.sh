# Gradle Wrapper Download Script
# Run this script to download the Gradle wrapper JAR file

WRAPPER_VERSION="8.4"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v${WRAPPER_VERSION}/gradle/wrapper/gradle-wrapper.jar"

echo "Downloading Gradle Wrapper ${WRAPPER_VERSION}..."
curl -L -o gradle/wrapper/gradle-wrapper.jar "${WRAPPER_URL}"

if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Gradle wrapper downloaded successfully!"
    echo "You can now build the project with: ./gradlew build"
else
    echo "Failed to download gradle-wrapper.jar"
    echo "Please download manually from: ${WRAPPER_URL}"
fi
