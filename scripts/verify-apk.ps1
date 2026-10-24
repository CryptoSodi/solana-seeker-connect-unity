param(
    [string]$UnityEditor = 'C:\Program Files\Unity\Hub\Editor\6000.6.0f1\Editor\Unity.exe',
    [string]$Apk = (Join-Path $PSScriptRoot '../Builds/SeekerConnectDemo.apk')
)
$ErrorActionPreference = 'Stop'
$android = Join-Path (Split-Path $UnityEditor) 'Data/PlaybackEngines/AndroidPlayer'
$buildTools = Join-Path $android 'SDK/build-tools/36.0.0'
& (Join-Path $android 'OpenJDK/bin/java.exe') -jar (Join-Path $buildTools 'lib/apksigner.jar') verify --verbose $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $Apk))
try {
    $descriptors = @('Lcom/community/seekerconnect/SeekerBridge;', 'Lcom/community/seekerconnect/WalletHostActivity;', 'Landroidx/activity/ComponentActivity;', 'Lcom/solanamobile/seekerconnect/SeekerConnect;')
    $allDex = ''
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^classes[0-9]*\.dex$') {
            $reader = New-Object IO.StreamReader($entry.Open(), [Text.Encoding]::GetEncoding(28591))
            try { $allDex += $reader.ReadToEnd() } finally { $reader.Dispose() }
        }
    }
    foreach ($descriptor in $descriptors) {
        if (-not $allDex.Contains($descriptor)) { throw "Missing DEX class: $descriptor" }
        Write-Output "Verified DEX: $descriptor"
    }
    foreach ($library in @('lib/arm64-v8a/libil2cpp.so', 'lib/arm64-v8a/libunity.so')) {
        if (-not $archive.GetEntry($library)) { throw "Missing native library: $library" }
        Write-Output "Verified native library: $library"
    }
} finally { $archive.Dispose() }
$hashAlgorithm = [Security.Cryptography.SHA256]::Create()
$inputStream = [IO.File]::OpenRead((Resolve-Path $Apk))
try { $hash = [BitConverter]::ToString($hashAlgorithm.ComputeHash($inputStream)).Replace('-', '').ToLowerInvariant(); Write-Output "SHA256: $hash" }
finally { $inputStream.Dispose(); $hashAlgorithm.Dispose() }
& (Join-Path $buildTools 'aapt.exe') dump badging $Apk | Select-String "^package:|^sdkVersion:|^targetSdkVersion:|^launchable-activity:|^native-code:"
if ($LASTEXITCODE -ne 0) { throw 'aapt inspection failed' }
