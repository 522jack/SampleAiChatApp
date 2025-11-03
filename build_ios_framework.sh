#!/bin/bash

# Script to build Kotlin framework for iOS with correct JAVA_HOME

set -e

# Set correct JAVA_HOME
export JAVA_HOME="/Users/evgeniy/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

# Navigate to project root
cd "$(dirname "$0")"

# Build the framework for iOS Simulator
echo "Building Kotlin framework for iOS Simulator..."
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

echo "Framework built successfully!"
echo "Location: shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework"