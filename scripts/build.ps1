param([string]$ReleaseTag)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$config = Get-Content -LiteralPath "$root/app/build.gradle.kts" -Raw
$versionMatch = [regex]::Match($config, 'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"')
if (!$versionMatch.Success) { throw 'Expected a versionName such as 1.0.0 in app/build.gradle.kts.' }
$version = $versionMatch.Groups[1].Value
if ($ReleaseTag -and $ReleaseTag -cne "v$version") {
    throw "Release tag '$ReleaseTag' does not match app version 'v$version'."
}
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
    $metadata = Get-Content -LiteralPath "$root/app/build/outputs/apk/debug/output-metadata.json" -Raw | ConvertFrom-Json
    if ($metadata.elements.Count -ne 1 -or $metadata.elements[0].versionName -cne $version) {
        throw 'Built APK version does not match app/build.gradle.kts.'
    }
    $fileName = "ChromecastRemote-$version.apk"
    $package = Join-Path $output $fileName
    Copy-Item -LiteralPath "$root/app/build/outputs/apk/debug/app-debug.apk" -Destination $package -Force
    $checksum = (Get-FileHash -LiteralPath $package -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath "$package.sha256" -Value "$checksum  $fileName" -Encoding ascii
    Write-Host "App ready: $package"
} finally { Pop-Location }
