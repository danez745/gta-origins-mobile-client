# GTA Origins Mobile Client

Forked and actively modifiable **SA-MP / open.mp mobile client** for **Grand Theft Auto: San Andreas (Android)**.

> Main author's:
[**Egor Kuzn**](https://github.com/bkuzn2) && [**Vadim**](https://github.com/kuzia15)

> Fork/maintenance:
> **danez745**

> **Status:** Active Development 🚧

## Fork notes

This repository is the working base for **GTA Origins Mobile Client** and will be customized for:

- client-side UI and gameplay changes
- automatic cache download inside the app
- client self-update flow from the app
- future server-specific integrations

The original upstream source is kept for reference, but this fork is expected to diverge over time.

## Features

* ✅ Android **ARM64** only
* ✅ **16 KB page size** support
* ✅ Fixed pickup synchronization
* ✅ Fixed jetpack synchronization
* ✅ Cache location moved to:

  ```text
  /storage/emulated/0/GTA/
  ```
* ✅ Optimized for modern Android devices

---

## Requirements

* Android 8.0+
* ARM64 device

---

## Installation

1. Install the APK.
2. Download the game cache:

   ```
   https://drive.google.com/file/d/1KmC1dNHkwTZ_mWT9PC8JuGSi1IXb2CZa/view?usp=drivesdk
   ```
3. Extract the cache to:

   ```text
   /storage/emulated/0/GTA/
   ```
4. Launch the game.

---

## Building

Clone the repository:

```bash
git clone https://github.com/kuzia15/SAMP-Mobile.git
```

Open the project in Android Studio and build using the default Gradle configuration.

---

## Project Status

This project is under active development. New fixes and improvements are added regularly.

---

## License

This project is provided for educational and research purposes only.

Grand Theft Auto, San Andreas and SA-MP are trademarks of their respective owners and are not affiliated with this project.
