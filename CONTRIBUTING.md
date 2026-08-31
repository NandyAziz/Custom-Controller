# Contributing

Thanks for helping improve Custom Controller.

## Before submitting a change

1. Keep the 13-byte protocol backward-compatible unless a protocol change is
   explicitly discussed.
2. Avoid adding heavy UI frameworks to the Android client without a clear need.
3. Keep latency-sensitive code allocation-light and avoid blocking the input
   path.
4. Document any new third-party dependency and its license.
5. Do not commit `local.properties`, build outputs, APKs, personal device data,
   or generated IDE files.

## Pull requests

Explain what changed, why it changed, and how you tested it. For controller or
network changes, include the Android version, Windows version, and whether USB
tethering/RNDIS was used.
