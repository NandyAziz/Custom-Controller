# Setup Guide

## End-user setup

### Windows

1. Install the ViGEm Bus driver separately from its official source.
2. Install or unpack the Custom Controller Server release.
3. Allow inbound UDP `5555` only as needed for the USB/RNDIS network.
4. Start `CustomController.Server.exe`.

### Android

1. Install the provided APK.
2. Connect the phone to the Windows PC with USB.
3. Enable USB tethering.
4. Open Custom Controller.
5. Confirm that the server packet counter increases.

The current Android client contains the Windows server address in
`UdpControllerSender.kt`. Change it to the PC's RNDIS IPv4 address before
building a version for another network setup.

## Developer setup

### Android

From `android-client`:

```powershell
.\gradlew.bat :app:assembleDebug
```

APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

### Windows server

From `windows-server`:

```powershell
dotnet build
```

For a self-contained single-file release:

```powershell
dotnet publish -c Release -r win-x64 --self-contained true /p:PublishSingleFile=true
```

The publish directory is under:

```text
bin\Release\net10.0-windows\win-x64\publish\
```
