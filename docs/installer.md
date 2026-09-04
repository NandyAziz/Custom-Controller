# Windows one-click setup

The repository can ship a small Windows setup folder containing the published
`CustomController.Server.exe` plus the two installer scripts.

## End-user flow

1. Extract the release ZIP.
2. Double-click `installer\\Install-CustomController.bat`.
3. Approve the Windows elevation prompt.
4. If ViGEmBus is missing, the script downloads the official ViGEmBus 1.22.0 installer, verifies its SHA-256 hash, and launches its normal interactive installer. The user still approves the driver install.
5. The setup adds a Private-profile UDP/5555 firewall rule and creates a Desktop shortcut.
6. Double-click **Custom Controller Server** whenever the controller is needed.

The server is self-contained, so .NET does not need to be installed separately.

## Release packaging

Before creating a release, publish the server and copy the resulting
`CustomController.Server.exe` into `installer\\` next to the scripts. Do not
commit build output to source control; include the EXE only in the release
artifact/ZIP.

The official ViGEmBus installer is not embedded in source control. The setup
script verifies the known SHA-256 before launching it. Review upstream release
notes before changing the pinned version.
