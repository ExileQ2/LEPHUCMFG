[CmdletBinding()]
param(
    [string]$BackendRoot = '',
    [string]$ReleaseNotes = '',
    [int]$MinSupportedVersionCode = 1,
    [string]$ExpectedSignerSha256 = '6b7ee8e849f5c825c3da49c3745777705962ce9c422671dcf75f11d55e14dbc0',
    [switch]$SkipBuild,
    [switch]$SkipBackendPublish
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$backendCandidate = if ([string]::IsNullOrWhiteSpace($BackendRoot)) {
    Join-Path $projectRoot '..\..\WebAPI\LPWebAPI'
} else {
    $BackendRoot
}
$backendRootResolved = (Resolve-Path -LiteralPath $backendCandidate).Path
$releaseNotesPath = Join-Path $PSScriptRoot 'release-notes-vi.txt'
if ([string]::IsNullOrWhiteSpace($ReleaseNotes) -and (Test-Path -LiteralPath $releaseNotesPath)) {
    $ReleaseNotes = (Get-Content -LiteralPath $releaseNotesPath -Raw -Encoding utf8).Trim()
}
$gradle = Join-Path $projectRoot 'gradlew.bat'
$metadataPath = Join-Path $projectRoot 'app\build\outputs\apk\release\output-metadata.json'

if (-not $SkipBuild) {
    & $gradle --no-daemon --console=plain clean testReleaseUnitTest assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Android build failed with exit code $LASTEXITCODE" }
}

if (-not (Test-Path -LiteralPath $metadataPath)) { throw "Missing build metadata: $metadataPath" }
$metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
$element = $metadata.elements | Select-Object -First 1
$versionCode = [int]$element.versionCode
$versionName = [string]$element.versionName
$apkPath = Join-Path (Split-Path -Parent $metadataPath) $element.outputFile
if (-not (Test-Path -LiteralPath $apkPath)) { throw "Missing release APK: $apkPath" }

$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$apkSigner = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1
if (-not $apkSigner) { throw 'Android apksigner was not found.' }

$signatureOutput = & $apkSigner verify --print-certs $apkPath 2>&1
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $signatureOutput" }
$signerLine = $signatureOutput | Where-Object { $_ -match 'certificate SHA-256 digest:' } | Select-Object -First 1
$actualSigner = (($signerLine -split ':', 2)[1] -replace '[^0-9a-fA-F]', '').ToLowerInvariant()
if ($actualSigner -ne $ExpectedSignerSha256.ToLowerInvariant()) {
    throw "Unexpected APK signer. Expected $ExpectedSignerSha256 but got $actualSigner"
}

$releaseDirectory = Join-Path $backendRootResolved 'wwwroot\apk\releases'
New-Item -ItemType Directory -Path $releaseDirectory -Force | Out-Null
$releaseName = "LPMFG-$versionCode.apk"
$releaseApk = Join-Path $releaseDirectory $releaseName
Copy-Item -LiteralPath $apkPath -Destination $releaseApk -Force

$hash = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant()
$size = (Get-Item -LiteralPath $releaseApk).Length
$manifest = [ordered]@{
    versionCode = $versionCode
    versionName = $versionName
    apkFileName = $releaseName
    sha256 = $hash
    sizeBytes = $size
    minSupportedVersionCode = $MinSupportedVersionCode
    releaseNotes = $ReleaseNotes
    publishedAtUtc = [DateTime]::UtcNow.ToString('o')
}
$manifestPath = Join-Path $releaseDirectory 'latest.json'
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding utf8

$legacyDirectory = Join-Path $backendRootResolved 'wwwroot\apk'
New-Item -ItemType Directory -Path $legacyDirectory -Force | Out-Null
Copy-Item -LiteralPath $releaseApk -Destination (Join-Path $legacyDirectory 'LPMFG.apk') -Force

$distRoot = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Path $distRoot -Force | Out-Null
$distApk = Join-Path $distRoot "LPMFG-$versionName.apk"
Copy-Item -LiteralPath $releaseApk -Destination $distApk -Force
Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $distRoot 'latest.json') -Force

if (-not $SkipBackendPublish) {
    $backendPublish = Join-Path $distRoot 'LPWebAPI'
    dotnet publish (Join-Path $backendRootResolved 'LPWebAPI.csproj') --configuration Release --output $backendPublish
    if ($LASTEXITCODE -ne 0) { throw "Backend publish failed with exit code $LASTEXITCODE" }
}

[pscustomobject]@{
    Version = "$versionName ($versionCode)"
    Apk = $distApk
    Manifest = (Join-Path $distRoot 'latest.json')
    BackendPublish = if ($SkipBackendPublish) { $null } else { Join-Path $distRoot 'LPWebAPI' }
    Sha256 = $hash
    SignerSha256 = $actualSigner
} | Format-List
