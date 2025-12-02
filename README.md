# Claude Chat - Kotlin Multiplatform

A cross-platform chat application built with Compose Multiplatform that integrates with Claude API. Supports Android, iOS, and Desktop (Windows, macOS, Linux).

## Features

- 💬 Real-time chat with Claude AI (Sonnet 4)
- 📱 Native apps for Android, iOS, and Desktop
- 🎨 Material3 design with dark/light theme support
- 💾 Local message history persistence
- ⚡ Streaming responses with Server-Sent Events (SSE)
- 🔒 Secure API key storage
- 📝 Customizable system prompts
- 📋 Copy message functionality

## Architecture

- **Clean Architecture** with separation of concerns
- **MVI Pattern** for UI state management
- **Shared Business Logic** in `commonMain`
- **Platform-specific implementations** where needed
- **Ktor** for HTTP networking
- **Kotlinx Serialization** for JSON
- **Multiplatform Settings** for local storage

## Project Structure

```
ClaudeChatKMP/
├── shared/                          # Shared Kotlin Multiplatform module
│   ├── commonMain/                  # Shared code for all platforms
│   │   ├── data/                    # Data layer
│   │   │   ├── local/              # Local storage
│   │   │   ├── model/              # API DTOs
│   │   │   ├── remote/             # API client
│   │   │   └── repository/         # Repository implementations
│   │   ├── domain/                  # Domain layer
│   │   │   └── model/              # Domain models
│   │   ├── presentation/            # Presentation layer
│   │   │   ├── chat/               # Chat screen ViewModel
│   │   │   ├── settings/           # Settings screen ViewModel
│   │   │   └── ui/                 # Compose UI components
│   │   └── di/                      # Dependency injection
│   ├── androidMain/                 # Android-specific code
│   ├── iosMain/                     # iOS-specific code
│   └── desktopMain/                 # Desktop-specific code
├── androidApp/                      # Android application
├── iosApp/                          # iOS application
└── desktopApp/                      # Desktop application
```

## Requirements

- **JDK 11** or higher
- **Android Studio** Hedgehog (2023.1.1) or newer
- **Xcode 15+** (for iOS development)
- **Gradle 8.2+**
- **Claude API Key** from https://console.anthropic.com/

## Setup

### 1. Clone the repository

```bash
git clone <repository-url>
cd ClaudeChatKMP
```

### 2. Get a Claude API Key

1. Visit https://console.anthropic.com/
2. Create an account or sign in
3. Navigate to API Keys
4. Generate a new API key
5. Copy the key (starts with `sk-ant-api...`)

### 3. Run the app

The first time you launch the app, you'll need to configure your API key in the Settings screen.

## Running on Different Platforms

### Android

1. Open the project in Android Studio
2. Select the `androidApp` configuration
3. Click Run or use:
   ```bash
   ./gradlew :androidApp:installDebug
   ```

### iOS

1. Open the project in Android Studio
2. Select an iOS simulator or device
3. Click Run, or:
   ```bash
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```
4. Open `iosApp/iosApp.xcodeproj` in Xcode and run

**Note:** iOS development requires a Mac with Xcode installed.

### Desktop

Run from Android Studio or use:

```bash
# Run directly
./gradlew :desktopApp:run

# Create distributable package
./gradlew :desktopApp:packageDistributionForCurrentOS
```

The packaged app will be in `desktopApp/build/compose/binaries/main/`

## Usage

1. **First Launch**: Configure your Claude API key in Settings (gear icon)
2. **Start Chatting**: Type a message and press send
3. **View History**: Messages are saved locally and persist between sessions
4. **Clear History**: Use the delete icon or go to Settings
5. **Customize**: Set a custom system prompt in Settings (optional)

## Dependencies

### Core
- Kotlin 2.0.21
- Compose Multiplatform 1.7.1
- Coroutines 1.9.0

### Networking
- Ktor 3.0.1

### Serialization
- Kotlinx Serialization 1.7.3
- Kotlinx DateTime 0.6.1

### Storage
- Multiplatform Settings 1.2.0

### Logging
- Napier 2.7.1

## API Integration

This app uses the Claude Messages API:

- **Endpoint**: `https://api.anthropic.com/v1/messages`
- **Model**: `claude-sonnet-4-20250929`
- **Features**: Streaming responses via SSE
- **Authentication**: API key in `x-api-key` header

## Security

- API keys are stored securely using platform-specific storage:
  - **Android**: SharedPreferences (app-private)
  - **iOS**: UserDefaults (app-private)
  - **Desktop**: Preferences API (user-specific)
- HTTPS is enforced for all API calls
- No data is sent to third parties except Claude API

## Building for Production

### Android

```bash
./gradlew :androidApp:assembleRelease
```

The APK will be in `androidApp/build/outputs/apk/release/`

### iOS

1. Open `iosApp/iosApp.xcodeproj` in Xcode
2. Select "Any iOS Device"
3. Product → Archive
4. Follow the App Store submission process

### Desktop

```bash
# macOS
./gradlew :desktopApp:packageDmg

# Windows
./gradlew :desktopApp:packageMsi

# Linux
./gradlew :desktopApp:packageDeb
```

## Troubleshooting

### Build Errors

1. **Clean and rebuild**:
   ```bash
   ./gradlew clean
   ./gradlew build
   ```

2. **Invalidate caches** in Android Studio: File → Invalidate Caches / Restart

### iOS Build Issues

1. Ensure you have the latest Xcode installed
2. Run `pod install` if you see CocoaPods errors
3. Clean Xcode build folder: Product → Clean Build Folder

### API Errors

- Verify your API key is correct
- Check your internet connection
- Ensure you have API credits available at https://console.anthropic.com/

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is for educational purposes. Please review Anthropic's Terms of Service for API usage.

## Acknowledgments

- Built with [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
- Powered by [Claude API](https://www.anthropic.com/api)
- Icons from [Material Icons](https://fonts.google.com/icons)

## Support

For issues and questions:
- Open an issue on GitHub
- Check the [Compose Multiplatform documentation](https://github.com/JetBrains/compose-multiplatform)
- Review [Claude API documentation](https://docs.anthropic.com/)