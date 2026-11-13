$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = Join-Path $root '.tools/jdk'
$env:ANDROID_HOME = Join-Path $root '.tools/android-sdk'
$env:ANDROID_USER_HOME = Join-Path $root '.tools/android-user'
$env:GRADLE_USER_HOME = Join-Path $root '.tools/gradle-cache'
Push-Location $root
try {
    & "$root/.tools/gradle-8.11.1/bin/gradle.bat" --no-daemon testDebugUnitTest lintDebug assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'Build or validation failed.' }
    $output = Join-Path $root 'outputs'
    New-Item -ItemType Directory -Force $output | Out-Null
    $package = Join-Path $output 'ChromecastRemote-0.3.4-test.apk'
    Copy-Item -LiteralPath "$root/app/build/outputs/apk/debug/app-debug.apk" -Destination $package -Force
    $checksum = (Get-FileHash -LiteralPath $package -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath "$package.sha256" -Value "$checksum  ChromecastRemote-0.3.4-test.apk" -Encoding ascii
    Write-Host "Test app ready: $package"
} finally { Pop-Location }
