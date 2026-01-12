# ArchDroid - Arch Linux for Android

ArchDroid is a Termux-like Android application that bootstraps and runs a complete Arch Linux ARM64 environment on Android devices without requiring root access.

## Features

- **Full Arch Linux Environment**: Runs genuine Arch Linux ARM64 using PRoot
- **No Root Required**: Works on standard Android installations
- **Package Management**: Complete pacman support for package installation
- **Terminal Emulation**: Custom terminal with ANSI color support
- **File Access**: Access to Android storage from within Linux
- **Persistent Sessions**: Multiple terminal sessions supported

## Requirements

- Android 7.0 (API 24) or higher
- ARM64 (AArch64) device architecture
- At least 1GB of free storage space
- Internet connection for initial installation

## Installation

### Using Pre-built APK

1. Download the latest APK from the releases page
2. Enable "Unknown sources" in Android settings
3. Install the APK

### Building from Source

#### Prerequisites

- **Temurin JDK 17** - Download from [Adoptium](https://adoptium.net/)
- **Android SDK** - Install via Android Studio or command line tools
- **Gradle 8.4** - Managed automatically by wrapper

#### Environment Setup

1. Install Temurin JDK 17:
```bash
# On Ubuntu/Debian
wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo apt-key add -
echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install temurin-17-jdk

# Verify installation
java -version
```

2. Set Android SDK environment variables:
```bash
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
```

3. Accept Android licenses:
```bash
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
```

4. Install required SDK components:
```bash
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

#### Building

```bash
# Clone the repository
git clone https://github.com/yourusername/archdroid.git
cd archdroid

# Build the APK
./gradlew assembleRelease

# The APK will be at: app/build/outputs/apk/release/app-release.apk
```

## Usage

### First Launch

On first launch, ArchDroid will download and extract the Arch Linux ARM64 root filesystem (~600MB). This process may take several minutes.

### Basic Commands

```bash
# Update package database
pacman -Syu

# Install a package
pacman -S package_name

# Search for packages
pacman -Ss search_term

# Remove a package
pacman -R package_name

# Access Android storage
cd /storage
cd /sdcard
```

### Special Keys

The terminal supports special key combinations through the extra keys bar:

- **CTRL** - Send Ctrl+C or other control characters
- **ALT** - Send Alt prefix
- **TAB** - Tab completion
- **ESC** - Escape key
- **Arrows** - Navigation in terminal
- **F1-F12** - Function keys

### Exiting

To exit ArchDroid:
1. Type `exit` or press Ctrl+D in the terminal
2. Use the back button to close the app

## Architecture

```
┌─────────────────────────────────────┐
│         Android System              │
│  ┌───────────────────────────────┐  │
│  │      ArchDroid App            │  │
│  │  ┌─────────────────────────┐  │  │
│  │  │    TerminalView (UI)    │  │  │
│  │  └─────────────────────────┘  │  │
│  │  ┌─────────────────────────┐  │  │
│  │  │  TerminalEmulator       │  │  │
│  │  └─────────────────────────┘  │  │
│  └───────────────────────────────┘  │
│               │                     │
│               ▼                     │
│  ┌───────────────────────────────┐  │
│  │    PRoot (User-space chroot)  │  │
│  └───────────────────────────────┘  │
│               │                     │
│               ▼                     │
│  ┌───────────────────────────────┐  │
│  │   Arch Linux ARM64 Rootfs     │  │
│  │   - bash shell                │  │
│  │   - pacman package manager    │  │
│  │   - coreutils                 │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

## Technical Details

- **Bootstrap**: Downloads official Arch Linux ARM rootfs from mirror servers
- **Container**: Uses PRoot for user-space containerization
- **Terminal**: Custom ANSI terminal emulator with xterm-256color support
- **Storage**: Bind mounts Android storage to /storage and /sdcard
- **Network**: Uses Android's network stack via bind mounts

## Troubleshooting

### "Incompatible Device" Error

Ensure your device uses ARM64 (AArch64) architecture. Check with:
```bash
adb shell getprop ro.product.cpu.abi
```

### Installation Fails

1. Check available storage space
2. Verify internet connection
3. Try clearing app data and reinstalling

### Terminal Display Issues

1. Try adjusting font size in settings
2. Report display issues on the GitHub issue tracker

### Package Installation Fails

1. Initialize pacman keys: `pacman-key --init`
2. Populate keyring: `pacman-key --populate archlinuxarm`

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- [Termux](https://termux.dev/) - For inspiring this project
- [PRoot](https://proot-me.github.io/) - For user-space chroot functionality
- [Arch Linux ARM](https://archlinuxarm.org/) - For the root filesystem
