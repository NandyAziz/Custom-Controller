# Custom Controller 🎮

Turn an Android phone into a **USB-connected, PS-style game controller for
Windows**.

The project is designed for people who want to play PC games without waiting
to buy a physical controller or relying on a third-party controller app.

> **Zero hardware controller required. Open source. USB-first.**

## Features

- 🎮 PS-style layout: △ ○ × □, L1/L2, R1/R2, SELECT, START, PS
- 🔌 USB tethering / RNDIS transport instead of Wi-Fi as the primary path
- ⚡ 250 Hz UDP heartbeat with immediate sends on state changes
- 🖥️ Windows virtual Xbox 360/XInput controller via ViGEm
- ↔️ Custom control positioning with saved layout
- 🛡️ Android network disconnect handling so the app can survive USB/RNDIS loss
- 🪶 Lightweight Android View UI without Jetpack Compose
- 🆓 MIT-licensed project code

## How it works

```text
Android Phone
     │
     │ USB + RNDIS
     ▼
Windows PC
     │
     │ UDP :5555
     ▼
Custom Controller Server
     │
     ▼
ViGEm Client / ViGEm Bus
     │
     ▼
Virtual Xbox 360 / XInput Controller
     │
     ▼
PC Game
```

The on-screen controls are **PlayStation-style**, while the Windows virtual
device is presented through XInput for broad PC game compatibility.

## Quick start for users

### Windows

Install the ViGEm Bus driver separately, then run:

```text
CustomController.Server.exe
```

The server listens on UDP `5555`.

### Android

Install the APK, connect the phone by USB, enable USB tethering, and open the
app.

The Android client must be configured with the Windows PC's RNDIS IPv4 address
in `UdpControllerSender.kt` when building a release for a different network.

See [`docs/setup.md`](docs/setup.md) for the complete setup.

## Developer build

### Android

```powershell
cd android-client
.\gradlew.bat :app:assembleDebug
```

APK:

```text
android-client\app\build\outputs\apk\debug\app-debug.apk
```

### Windows server

```powershell
cd windows-server
dotnet build
```

To create a self-contained single-file Windows release:

```powershell
dotnet publish -c Release -r win-x64 --self-contained true /p:PublishSingleFile=true
```

The executable is produced under:

```text
bin\Release\net10.0-windows\win-x64\publish\
```

## Protocol

The transport uses a fixed **13-byte UDP packet**. See
[`docs/protocol.md`](docs/protocol.md).

## Releases

For end users, the recommended GitHub Release should provide two artifacts:

- Android APK
- Windows x64 server ZIP containing `CustomController.Server.exe`

Build and release guidance is in [`docs/release.md`](docs/release.md).

## Security and privacy

The UDP protocol is unauthenticated and unencrypted. It is intended for a
private USB/RNDIS connection between the phone and PC. **Do not expose UDP 5555
to the public Internet.**

The app does not need an online account or cloud service for controller input.

See [`SECURITY.md`](SECURITY.md).

## Third-party software

The Windows server uses `Nefarius.ViGEm.Client` and requires the ViGEm Bus driver
installed separately. The Android client uses Kotlin coroutines.

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) for attribution and
license information.

## Trademarks

Custom Controller is an independent project and is **not affiliated with,
endorsed by, or sponsored by Sony Interactive Entertainment, Microsoft,
Nefarius, or DroidJoy**.

“PlayStation”, “PS2”, “PS3”, the PlayStation logo, and the △ ○ × □ symbols are
trademarks of their respective owners. This project uses PS-style control
labels for interface familiarity; it does not claim official PlayStation
branding.

## License

Original project code is released under the [MIT License](LICENSE).
Third-party components retain their respective licenses; see
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## Status

This is an independent hobby/open-source project. Hardware, Android versions,
Windows configurations, and individual games can affect compatibility and
latency.

See [`docs/roadmap.md`](docs/roadmap.md) for planned improvements.
