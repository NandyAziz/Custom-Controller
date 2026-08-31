# Security

## Network model

The Android client sends controller state over UDP port `5555`. The current
implementation is intended for a direct USB tethering/RNDIS network between the
phone and the Windows PC.

The protocol is unauthenticated and unencrypted. Do not expose UDP/5555 to an
untrusted network or the public Internet.

## Reporting a security issue

Please do not publish a working exploit in a public issue. Open a private
security report through the repository's GitHub security features when
available, or contact the project maintainer through the contact method listed
in the repository profile.

## Safe defaults

- Keep UDP/5555 restricted to the USB/RNDIS interface where practical.
- Do not forward UDP/5555 from the router to the Internet.
- Only install the Windows executable and Android APK from a source you trust.
