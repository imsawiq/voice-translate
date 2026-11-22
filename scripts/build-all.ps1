param(
    [switch] $NoClean
)

$ErrorActionPreference = "Stop"

$Versions = @("26.1.x", "26.2.x", "26.3.x")
$Loaders = @("fabric", "neoforge")
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Dist = Join-Path $Root "dist"

if (Test-Path -LiteralPath $Dist) {
    Get-ChildItem -LiteralPath $Dist -Filter "voice-translate-*.jar" -File | Remove-Item -Force
}

$Tasks = @()
foreach ($Version in $Versions) {
    foreach ($Loader in $Loaders) {
        if (-not $NoClean) {
            $Tasks += ":$Version-${Loader}:clean"
        }
        $Tasks += ":$Version-${Loader}:buildAndCollect"
    }
}

& (Join-Path $Root "gradlew.bat") --console=plain @Tasks
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Built $($Versions.Count * $Loaders.Count) jars into $Dist"
