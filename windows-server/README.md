# Custom Controller Server

Windows-side receiver for the Custom Controller Android client.

## Runtime requirements

- Windows 10/11
- ViGEm Bus driver installed separately
- No .NET runtime is required when using the self-contained single-file publish
  produced by the release command.

## Run from source

```powershell
dotnet run
```

## Publish a standalone executable

```powershell
dotnet publish -c Release -r win-x64 --self-contained true /p:PublishSingleFile=true
```

The executable is written to:

```text
bin\Release\net10.0-windows\win-x64\publish\CustomController.Server.exe
```

## Network

The server listens on UDP port `5555` on all IPv4 interfaces. Restrict the
Windows Firewall rule to the USB/RNDIS network where practical.

## Input format

The server expects the fixed 13-byte packet documented in
[`../docs/protocol.md`](../docs/protocol.md).
