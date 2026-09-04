$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot\windows-server

dotnet publish -c Release -r win-x64 --self-contained true /p:PublishSingleFile=true

$publish = Join-Path $PWD 'bin\Release\net10.0-windows\win-x64\publish'
$installerDir = Join-Path $PSScriptRoot 'installer'
New-Item -ItemType Directory -Path $installerDir -Force | Out-Null
Copy-Item (Join-Path $publish 'CustomController.Server.exe') $installerDir -Force

Write-Host ''
Write-Host 'Windows release ready.' -ForegroundColor Green
Write-Host "Published EXE: $publish\CustomController.Server.exe"
Write-Host "Installer bundle: $installerDir"
Write-Host 'The EXE is ignored by Git and should be attached to a GitHub Release.'
