# Quick Start Guide

## Prerequisites

- JDK 11 or higher
- Android Studio Hedgehog (2023.1.1) or newer
- For iOS: Mac with Xcode 15+
- Claude API key from https://console.anthropic.com/

## Getting Started

### 1. Open in Android Studio

1. Launch Android Studio
2. Select "Open an Existing Project"
3. Navigate to the `ClaudeChatKMP` folder and click "Open"
4. Wait for Gradle sync to complete (this may take a few minutes on first launch)

### 2. Get Your Claude API Key

1. Visit https://console.anthropic.com/
2. Sign up or log in
3. Go to "API Keys" section
4. Click "Create Key"
5. Copy your API key (starts with `sk-ant-api...`)
6. Keep this key safe - you'll need it in the app

### 3. Run on Android

#### Using Android Studio:
1. Select `androidApp` from the run configurations dropdown
2. Choose an emulator or connected device
3. Click the Run button (green triangle)

#### Using Command Line:
```bash
./gradlew :androidApp:installDebug
```

### 4. Run on iOS

#### Using Android Studio:
1. Ensure you're on a Mac with Xcode installed
2. Select an iOS simulator from the device dropdown
3. Click the Run button

#### Using Xcode:
```bash
# First, build the shared framework
./gradlew :shared:embedAndSignAppleFrameworkForXcode

# Then open in Xcode
open iosApp/iosApp.xcodeproj
```

Select a simulator or device and press Cmd+R to run.

### 5. Run on Desktop

#### Using Android Studio:
1. Select `desktopApp` from the run configurations dropdown
2. Click the Run button

#### Using Command Line:
```bash
./gradlew :desktopApp:run
```

## First Launch Setup

1. **Launch the app** on your chosen platform
2. **Tap the Settings icon** (gear icon in the top-right)
3. **Enter your Claude API Key** in the API Key field
4. **Tap "Save API Key"**
5. **Go back** to the chat screen
6. **Start chatting!**

## Testing the App

Try these example prompts:
- "Hello! Can you introduce yourself?"
- "Explain quantum computing in simple terms"
- "Write a haiku about programming"
- "What are the benefits of Kotlin Multiplatform?"

## Common Issues

### Build Fails

**Solution:**
```bash
./gradlew clean
./gradlew build --refresh-dependencies
```

### Android Studio Can't Find JDK

**Solution:**
1. File → Project Structure
2. SDK Location → JDK Location
3. Browse to your JDK installation
4. Click Apply

### iOS Build Fails

**Solution:**
```bash
# Clean the Xcode build
cd iosApp
xcodebuild clean

# Rebuild the shared framework
cd ..
./gradlew :shared:clean
./gradlew :shared:embedAndSignAppleFrameworkForXcode
```

### "API Key Not Configured" Error

**Solution:**
1. Make sure you've entered your API key in Settings
2. Tap "Save API Key"
3. Return to the chat screen
4. If issue persists, restart the app

### Network/API Errors

**Check:**
- Internet connection is active
- API key is correct (no extra spaces)
- You have available credits at https://console.anthropic.com/
- Firewall isn't blocking the app

## Building Distributions

### Android APK
```bash
./gradlew :androidApp:assembleRelease
# APK location: androidApp/build/outputs/apk/release/
```

### iOS IPA
Use Xcode to archive and export:
1. Open iosApp/iosApp.xcodeproj in Xcode
2. Product → Archive
3. Distribute App

### Desktop Packages

**macOS:**
```bash
./gradlew :desktopApp:packageDmg
```

**Windows:**
```bash
./gradlew :desktopApp:packageMsi
```

**Linux:**
```bash
./gradlew :desktopApp:packageDeb
```

Packages will be in: `desktopApp/build/compose/binaries/main/`

## Project Structure Overview

```
ClaudeChatKMP/
├── shared/              # Shared code (business logic, UI)
│   ├── commonMain/      # Platform-agnostic code
│   ├── androidMain/     # Android-specific code
│   ├── iosMain/         # iOS-specific code
│   └── desktopMain/     # Desktop-specific code
├── androidApp/          # Android app wrapper
├── iosApp/              # iOS app wrapper
└── desktopApp/          # Desktop app wrapper
```

## Features

✅ Real-time streaming responses
✅ Message history (persisted locally)
✅ Dark/Light theme (system default)
✅ Material3 design
✅ Copy message to clipboard
✅ Clear chat history
✅ Custom system prompts
✅ Secure API key storage

## Next Steps

- Customize the system prompt in Settings
- Explore the codebase structure
- Add your own features
- Share feedback!

## Need Help?

- Check the main [README.md](README.md) for detailed documentation
- Review the code comments for implementation details
- File issues on GitHub
- Check Anthropic's API documentation: https://docs.anthropic.com/

Happy chatting with Claude! 🎉
