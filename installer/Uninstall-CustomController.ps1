#requires -version 5.1
[CmdletBinding()]
param(
    [string]$InstallRoot = "$env:ProgramFiles\Custom Controller"
)
$ErrorActionPreference = 'Stop'

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}
if (-not (Test-IsAdministrator)) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList ('-NoProfile -ExecutionPolicy Bypass -File "{0}"' -f $PSCommandPath) | Out-Null
    exit
}

Get-Process -Name 'CustomController.Server' -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Remove-NetFirewallRule -DisplayName 'Custom Controller UDP 5555' -ErrorAction SilentlyContinue
$desktop = [Environment]::GetFolderPath('CommonDesktopDirectory')
Remove-Item (Join-Path $desktop 'Custom Controller Server.lnk') -Force -ErrorAction SilentlyContinue
Remove-Item $InstallRoot -Recurse -Force -ErrorAction SilentlyContinue
Write-Host 'Custom Controller server files and shortcut removed.' -ForegroundColor Green
Write-Host 'ViGEmBus was not removed automatically.'
