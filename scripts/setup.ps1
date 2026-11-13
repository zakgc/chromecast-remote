param([switch]$AcceptAndroidLicenses)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$tools = Join-Path $root '.tools'
New-Item -ItemType Directory -Force $tools | Out-Null
function Fetch($url, $file) {
    if (!(Test-Path $file)) {
        & curl.exe --fail --location --retry 3 --output $file $url
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $url" }
    }
}
if (!(Test-Path "$tools/jdk")) {
    Fetch 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' "$tools/jdk.zip"
    Expand-Archive -LiteralPath "$tools/jdk.zip" -DestinationPath "$tools/java-unpack" -Force
    $jdk = Get-ChildItem "$tools/java-unpack" -Directory | Select-Object -First 1
    Move-Item -LiteralPath $jdk.FullName -Destination "$tools/jdk"
}
if (!(Test-Path "$tools/gradle-8.11.1")) {
    Fetch 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip' "$tools/gradle.zip"
    Expand-Archive -LiteralPath "$tools/gradle.zip" -DestinationPath $tools -Force
}
if (!(Test-Path "$tools/android-sdk/cmdline-tools/latest/bin/sdkmanager.bat")) {
    Fetch 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' "$tools/android-tools.zip"
    Expand-Archive -LiteralPath "$tools/android-tools.zip" -DestinationPath "$tools/android-unpack" -Force
    New-Item -ItemType Directory -Force "$tools/android-sdk/cmdline-tools" | Out-Null
    Move-Item -LiteralPath "$tools/android-unpack/cmdline-tools" -Destination "$tools/android-sdk/cmdline-tools/latest"
}
$env:JAVA_HOME = "$tools/jdk"
$env:ANDROID_HOME = "$tools/android-sdk"
$env:ANDROID_USER_HOME = "$tools/android-user"
$env:GRADLE_USER_HOME = "$tools/gradle-cache"
$manager = "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat"
if ($AcceptAndroidLicenses) {
    1..100 | ForEach-Object { 'y' } | & $manager --licenses
    if ($LASTEXITCODE -ne 0) { throw 'Android SDK license setup failed.' }
}
& $manager 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0) { throw 'Android SDK installation failed. Review licenses with sdkmanager --licenses.' }
$sdk = $env:ANDROID_HOME.Replace('\','/')
Set-Content -LiteralPath "$root/local.properties" -Value "sdk.dir=$sdk" -Encoding ascii
& "$tools/gradle-8.11.1/bin/gradle.bat" wrapper --gradle-version 8.11.1 --distribution-type bin
if ($LASTEXITCODE -ne 0) { throw 'Gradle wrapper creation failed.' }
Write-Host 'Ready. Run scripts/build.ps1 to build and test the APK.'
