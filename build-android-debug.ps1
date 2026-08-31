$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot\android-client
.\gradlew.bat :app:assembleDebug

Write-Host ''
Write-Host 'APK build complete.'
Write-Host (Join-Path $PWD 'app\build\outputs\apk\debug\app-debug.apk')
