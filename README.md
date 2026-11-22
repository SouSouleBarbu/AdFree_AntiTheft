# AdFree AntiTheft

**AdFree AntiTheft** is a lightweight, privacy-focused, open-source anti-theft security application for Android. Built without ads, trackers, or proprietary dependencies, it helps protect your device against theft.

## Features

- **Anti-Theft Detection:** Triggers an immediate alarm when unauthorized movement, charger disconnection, or suspicious activity is detected.
- **100% Free & Open Source (FOSS):** No advertisements, no analytics, and no proprietary SDKs (no Google Play Services or Firebase).
- **Offline & Private:** Works completely locally on your device without sending personal data to external servers.
- **Lightweight & Battery-Friendly:** Designed to run reliably in the background with minimal resource usage.

## Installation

### F-Droid
*(Coming soon — once approved, the app will be available directly via the official F-Droid repository.)*

### Build from Source
1. Clone this repository:
   ```bash
   git clone https://github.com/SouSouleBarbu/AdFree_AntiTheft.git
   cd AdFree_AntiTheft
   ```
2. Build the debug or release APK using Gradle:
   ```bash
   ./gradlew assembleRelease
   ```
   The generated APK will be located in `app/build/outputs/apk/release/`.

## Permissions

To function properly as an anti-theft tool, **AdFree AntiTheft** only requests the permissions strictly necessary for its core features:

- **Foreground Service / Wake Lock:** Keeps the monitoring service active while armed.
- **Modify Audio Settings / Vibrate:** Ensures the alarm sounds at maximum volume even if the device is in silent mode.

## Contributing

Contributions, bug reports, and feature requests are welcome! Feel free to open an issue or submit a pull request.

## License

This project is licensed under the **GPL-3.0** License — see the [LICENSE](LICENSE) file for details.