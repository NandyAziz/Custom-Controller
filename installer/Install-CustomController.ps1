#requires -version 5.1
[CmdletBinding()]
param(
    [string]$InstallRoot = "$env:ProgramFiles\Custom Controller"
)

$ErrorActionPreference = 'Stop'

$AppName = 'Custom Controller'
$ServerExeName = 'CustomController.Server.exe'
$DriverUrl = 'https://github.com/nefarius/ViGEmBus/releases/download/v1.22.0/ViGEmBus_1.22.0_x64_x86_arm64.exe'
$DriverSha256 = '89220A7865076B342892F98865F3499FB7C4CFD673159E89D352C360FD014C6A'
$FirewallRuleName = 'Custom Controller UDP 5555'
$ShortcutName = 'Custom Controller Server.lnk'

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Ensure-Administrator {
    if (Test-IsAdministrator) { return }
    $args = '-NoProfile -ExecutionPolicy Bypass -File "{0}"' -f $PSCommandPath
    Start-Process powershell.exe -Verb RunAs -ArgumentList $args | Out-Null
    exit
}

function Test-ViGEmBusInstalled {
    $paths = @(
        'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*',
        'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*'
    )
    foreach ($path in $paths) {
        $items = Get-ItemProperty $path -ErrorAction SilentlyContinue
        foreach ($item in $items) {
            if ($item.DisplayName -like '*ViGEmBus*') { return $true }
        }
    }

    try {
        $devices = Get-PnpDevice -PresentOnly -ErrorAction Stop
        if ($devices | Where-Object { $_.FriendlyName -match 'ViGEm' -or $_.Manufacturer -match 'Nefarius' }) {
            return $true
        }
    } catch { }

    return $false
}

function Install-ViGEmBus {
    Write-Host 'ViGEmBus was not detected.'
    Write-Host 'Downloading the official ViGEmBus installer...' -ForegroundColor Cyan

    $temp = Join-Path $env:TEMP 'CustomController'
    New-Item -ItemType Directory -Path $temp -Force | Out-Null
    $installer = Join-Path $temp 'ViGEmBus_1.22.0_x64_x86_arm64.exe'

    Invoke-WebRequest -Uri $DriverUrl -OutFile $installer
    $hash = (Get-FileHash -Path $installer -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($hash -ne $DriverSha256) {
        Remove-Item $installer -Force -ErrorAction SilentlyContinue
        throw "ViGEmBus installer SHA-256 mismatch. Expected $DriverSha256 but got $hash."
    }

    Write-Host 'Starting the official ViGEmBus installer. Please approve its prompts.' -ForegroundColor Yellow
    $p = Start-Process -FilePath $installer -Wait -PassThru
    if ($p.ExitCode -ne 0) {
        throw "ViGEmBus installer exited with code $($p.ExitCode)."
    }
}

function Ensure-FirewallRule {
    $existing = Get-NetFirewallRule -DisplayName $FirewallRuleName -ErrorAction SilentlyContinue
    if (-not $existing) {
        New-NetFirewallRule -DisplayName $FirewallRuleName `
            -Direction Inbound -Action Allow -Protocol UDP -LocalPort 5555 `
            -Profile Private | Out-Null
        Write-Host 'Added Windows Firewall rule for UDP 5555 (Private profile).' -ForegroundColor Green
    }
}

function Ensure-Shortcut {
    $desktop = [Environment]::GetFolderPath('CommonDesktopDirectory')
    $shortcutPath = Join-Path $desktop $ShortcutName
    $exePath = Join-Path $InstallRoot $ServerExeName

    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = $exePath
    $shortcut.WorkingDirectory = $InstallRoot
    $shortcut.Description = $AppName
    $shortcut.Save()
    Write-Host "Created shortcut: $shortcutPath" -ForegroundColor Green
}

Ensure-Administrator

$scriptRoot = Split-Path -Parent $PSCommandPath
$bundledExe = Join-Path $scriptRoot $ServerExeName

if (-not (Test-Path $bundledExe)) {
    throw "Bundled $ServerExeName was not found next to the installer script. Build/publish the Windows server first and place the EXE in installer\\."
}

New-Item -ItemType Directory -Path $InstallRoot -Force | Out-Null
Copy-Item $bundledExe (Join-Path $InstallRoot $ServerExeName) -Force

if (-not (Test-ViGEmBusInstalled)) {
    Install-ViGEmBus
} else {
    Write-Host 'ViGEmBus detected.' -ForegroundColor Green
}

Ensure-FirewallRule
Ensure-Shortcut

Write-Host ''
Write-Host "$AppName installation complete." -ForegroundColor Green
Write-Host "Installed to: $InstallRoot"
Write-Host 'Use the Desktop shortcut to start the Windows server.'
