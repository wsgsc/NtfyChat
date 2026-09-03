# NtfyChat - Open Source Instant Messaging App

[English](README.md) | [简体中文](README.zh-CN.md)

---

## Overview
NtfyChat is an open-source instant messaging application based on [ntfy](https://github.com/binwiederhier/ntfy). 

**How it works**: You can set up a subscription topic, username, and encryption password. When two users share the same subscription topic and encryption password, they can communicate securely. The implementation uses symmetric encryption algorithms, making it simple yet effective for private messaging.

## Features
- 📱 Real-time messaging based on ntfy protocol
- 🚀 No server configuration needed - uses official ntfy.sh by default
- 🔒 End-to-end encryption support
- 🌐 Self-hosted server option
- 📎 File and image sharing
- 🎨 Material Design UI
- 🔔 Push notifications
- 💬 Group chat support

## Download
- **Google Play Store**: Coming soon
- **F-Droid**: Coming soon
- **GitHub Releases**: [Download APK](https://github.com/wsgsc/NtfyChat/releases)

## Build Instructions
For detailed build instructions, please refer to the [official ntfy docs](https://docs.ntfy.sh/develop/#android-app).

```bash
# Clone the repository
git clone https://github.com/wsgsc/NtfyChat.git
cd NtfyChat

# Build the app
./gradlew assembleRelease
```

## Technology Stack
- **Language**: Kotlin
- **Architecture**: MVVM + Repository Pattern
- **Database**: Room (SQLite)
- **Networking**: OkHttp
- **UI**: Material Design Components
- **Dependency Injection**: Manual DI
- **Push Notifications**: Firebase Cloud Messaging (Play variant only)

## Contributing
We welcome contributions! Please feel free to submit pull requests or open issues.

## Translations
We use [Weblate](https://hosted.weblate.org/projects/ntfy/) for translations. Contributions are welcome!

## License
Distributed under the [Apache License 2.0](LICENSE).

Based on [ntfy](https://github.com/binwiederhier/ntfy) by [Philipp C. Heckel](https://heckel.io).
