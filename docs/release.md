# GitHub Release Guide

The recommended distribution model is:

- Keep source code in the repository.
- Attach `app-release.apk` (or the chosen APK name) to a GitHub Release.
- Attach a ZIP containing the self-contained Windows server executable.
- Keep generated binaries out of the Git repository itself.

## Windows package

Build:

```powershell
dotnet publish -c Release -r win-x64 --self-contained true /p:PublishSingleFile=true
```

Package the resulting `publish` directory into a ZIP containing at minimum:

```text
CustomController.Server.exe
README.txt
```

## Android package

Build a release APK using the Android signing configuration chosen by the
maintainer. Do not publish a debug APK as the main public release artifact.

## Checksums

Generate SHA-256 checksums for every release artifact and publish them in the
GitHub Release notes. On Windows PowerShell:

```powershell
Get-FileHash .\CustomController.Server-Windows-x64.zip -Algorithm SHA256
Get-FileHash .\CustomController.apk -Algorithm SHA256
```

## Third-party licensing

Keep `THIRD_PARTY_NOTICES.md` in the source repository. If a release ZIP or
installer redistributes third-party binaries, include the applicable license
notices with the binary distribution as required by those licenses.

The current project intentionally expects the ViGEm Bus driver to be installed
separately rather than bundling its installer.

## Android distribution verification

Android's developer verification protections are scheduled to take effect on
September 30, 2026 for participating stores and certified devices in Indonesia
and certain other countries. Review the current Android developer-verification
guidance before distributing the APK broadly outside Google Play. Hobbyists can
use Google's limited-distribution path for small user groups.

## Windows installer bundle

For the user-facing Windows ZIP, put the published `CustomController.Server.exe` next to the scripts in `installer/`. The setup script handles ViGEmBus detection, firewall setup, and Desktop shortcut creation.
