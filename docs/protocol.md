# UDP Protocol

The controller protocol is a fixed 13-byte packet.

| Byte | Meaning | Encoding |
|---:|---|---|
| 0 | Sequence | unsigned 8-bit, wraps at 255 |
| 1 | Digital buttons | bitmask |
| 2 | PS + D-pad | bitmask |
| 3-4 | Left X | signed Int16, little-endian |
| 5-6 | Left Y | signed Int16, little-endian |
| 7-8 | Right X | signed Int16, little-endian |
| 9-10 | Right Y | signed Int16, little-endian |
| 11 | Left trigger | 0..255 |
| 12 | Right trigger | 0..255 |

## Digital byte

- bit 0: Cross
- bit 1: Circle
- bit 2: Square
- bit 3: Triangle
- bit 4: L1
- bit 5: R1
- bit 6: Select
- bit 7: Start

## Extra byte

- bit 0: PS / Guide
- bit 1: D-pad Up
- bit 2: D-pad Down
- bit 3: D-pad Left
- bit 4: D-pad Right

The Android sender sends on state changes and also on a 250 Hz heartbeat.
The Windows server rejects stale sequence numbers using an 8-bit wrap-aware
comparison.
