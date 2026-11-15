# OptiClient 전체 빌드: HUD 모드 5종 + 런처 → dist\OptiClient (+ 공유용 dist\OptiClient.zip)
# 사용법:  powershell -ExecutionPolicy Bypass -File .\build-all.ps1
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$dist = Join-Path $root 'dist\OptiClient'
$hud = Join-Path $dist 'hud'

$mods = @('mod-fabric-26.3', 'mod-fabric-1.21.11', 'mod-fabric-1.21.1', 'mod-fabric-1.20.1', 'mod-forge-1.8.9')
foreach ($m in $mods) {
    Write-Host "== $m 빌드 중..." -ForegroundColor Cyan
    Push-Location (Join-Path $root $m)
    try {
        & .\gradlew.bat build --console=plain -q
        if ($LASTEXITCODE -ne 0) { throw "$m 빌드 실패" }
    } finally { Pop-Location }
}

Write-Host "== 런처 빌드 중..." -ForegroundColor Cyan
dotnet publish (Join-Path $root 'launcher\OptiClientLauncher\OptiClientLauncher.csproj') `
    -c Release -r win-x64 -p:SelfContained=false -p:PublishSingleFile=true -p:DebugType=None -o $dist -nologo -v q
if ($LASTEXITCODE -ne 0) { throw '런처 빌드 실패' }

New-Item -ItemType Directory -Force $hud | Out-Null
Get-ChildItem $hud -Filter 'opticlient-hud-*.jar' | Remove-Item
foreach ($m in $mods) {
    Get-ChildItem (Join-Path $root "$m\build\libs") -Filter 'opticlient-hud-*.jar' |
        Where-Object { $_.Name -notmatch '-(sources|dev|all)\.jar$' } |
        Copy-Item -Destination $hud
}

# 배포에 필요 없는 파일 정리 + 사용법 + 공유용 zip
Get-ChildItem $dist -File | Where-Object { $_.Name -notin @('OptiClientLauncher.exe', '사용법.txt') } | Remove-Item
Copy-Item (Join-Path $root 'dist-readme.txt') (Join-Path $dist '사용법.txt') -Force
$zip = Join-Path $root 'dist\OptiClient.zip'
if (Test-Path $zip) { Remove-Item $zip }
# Compress-Archive 는 실행 중인 런처 exe 를 못 읽어서 직접 묶는다
Add-Type -AssemblyName System.IO.Compression
$fs = [IO.File]::Open($zip, 'CreateNew')
$archive = New-Object IO.Compression.ZipArchive($fs, [IO.Compression.ZipArchiveMode]::Create)
foreach ($f in Get-ChildItem $dist -Recurse -File) {
    $entry = $archive.CreateEntry('OptiClient/' + $f.FullName.Substring($dist.Length + 1).Replace('\', '/'), [IO.Compression.CompressionLevel]::Optimal)
    $in = [IO.File]::Open($f.FullName, 'Open', 'Read', 'ReadWrite, Delete')
    $out = $entry.Open()
    try { $in.CopyTo($out) } finally { $out.Dispose(); $in.Dispose() }
}
$archive.Dispose()
$fs.Dispose()

Write-Host ""
Write-Host "완료: $dist\OptiClientLauncher.exe" -ForegroundColor Green
Write-Host "공유용: $zip" -ForegroundColor Green
Get-ChildItem $hud | ForEach-Object { Write-Host "  hud\$($_.Name)" }
