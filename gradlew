#!/bin/sh
# Gradle start up script for POSIX generated for GitHub Actions & Klencod IDE
if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    exec java -Xmx64m -Xms64m -jar "gradle/wrapper/gradle-wrapper.jar" "$@"
else
    exec gradle "$@"
fi
