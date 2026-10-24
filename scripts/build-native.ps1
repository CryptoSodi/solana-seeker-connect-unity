param([string]$UnityEditor = 'C:\Program Files\Unity\Hub\Editor\6000.6.0f1\Editor\Unity.exe', [switch]$Clean)
$ErrorActionPreference = 'Stop'
$android = Join-Path (Split-Path $UnityEditor) 'Data/PlaybackEngines/AndroidPlayer'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = Join-Path $android 'OpenJDK' }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $android 'SDK' }
Push-Location (Join-Path $PSScriptRoot '../NativeAndroid')
try {
    if ($Clean) { & ./gradlew.bat :seekerBridge:clean --console=plain; if ($LASTEXITCODE -ne 0) { throw 'Gradle clean failed' } }
    & ./gradlew.bat :seekerBridge:assembleRelease :seekerBridge:testDebugUnitTest --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Native build/tests failed: $LASTEXITCODE" }
} finally { Pop-Location }
