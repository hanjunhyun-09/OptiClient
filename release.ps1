# 새 버전 배포: 버전 올리기 → 전체 빌드 → git 커밋·태그·푸시 → GitHub 릴리스(OptiClient.zip) 올리기
# 친구들 런처는 켤 때 이 릴리스를 보고 자동으로 업데이트한다.
# 사용법:  powershell -ExecutionPolicy Bypass -File .\release.ps1 1.0.1 "바뀐 점 설명"
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Notes = ''
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw '버전은 1.2.3 형식으로 적어 주세요.' }
$tag = "v$Version"
if (-not $Notes) { $Notes = "OptiClient $tag" }

$gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
if (-not $gh) { $gh = Join-Path $env:ProgramFiles 'GitHub CLI\gh.exe' }
if (-not (Test-Path $gh)) { throw 'GitHub CLI(gh)가 없어요. winget install GitHub.cli 로 설치하세요.' }

# 1) 런처 버전 올리기
$csproj = Join-Path $root 'launcher\OptiClientLauncher\OptiClientLauncher.csproj'
$xml = [IO.File]::ReadAllText($csproj)
$xml = [Text.RegularExpressions.Regex]::Replace($xml, '<Version>[^<]*</Version>', "<Version>$Version</Version>")
[IO.File]::WriteAllText($csproj, $xml)
Write-Host "== 런처 버전: $Version" -ForegroundColor Cyan

# 2) 전체 빌드 (dist\OptiClient.zip 생성)
& (Join-Path $root 'build-all.ps1')
$zip = Join-Path $root 'dist\OptiClient.zip'
if (-not (Test-Path $zip)) { throw 'dist\OptiClient.zip 이 없어요.' }

# 3) git 커밋·태그·푸시
git -C $root add -A
git -C $root commit -m "OptiClient $tag" -m $Notes
if ($LASTEXITCODE -ne 0) { Write-Host '(바뀐 파일이 없어 커밋은 건너뜀)' }
git -C $root tag $tag
git -C $root push
git -C $root push origin $tag
if ($LASTEXITCODE -ne 0) { throw 'git push 실패' }

# 4) GitHub 릴리스
& $gh release create $tag $zip --title "OptiClient $tag" --notes $Notes
if ($LASTEXITCODE -ne 0) { throw 'GitHub 릴리스 만들기 실패' }
Write-Host ""
Write-Host "배포 완료: $tag  (친구들 런처는 다음에 켤 때 업데이트를 물어봐요)" -ForegroundColor Green
