<#
.SYNOPSIS
  Runs the Stata tests (spec 11) in batch mode.

.DESCRIPTION
  Each src/test/stata/test_*.do runs in its own fresh directory under target/stata-tests/, with:
    - stata/ first on the ado-path (the command under test);
    - CTOSIM_JAR and CTOSIM_CONVERTER pointing to target/stata-package/ (build it first with
      scripts/build-stata-package.ps1, or pass -Build);
    - the globals CTOSIM_REPO (repository root) and CTOSIM_TESTDIR (src/test/stata), for fixtures.
  A test passes when its do-file runs to the end without error. Other .do files under
  src/test/stata/ are helpers and are not run on their own.

.EXAMPLE
  .\scripts\stata-tests.ps1 -Build
  .\scripts\stata-tests.ps1 -Filter test_options*
#>
param([switch]$Build, [string]$Filter = 'test_*.do', [string]$TestDir = '')

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = 'C:\Program Files\StataNow19\utilities\java\windows-x64\zulu-jdk21.0.11'
}

if ($Build) {
    & (Join-Path $PSScriptRoot 'build-stata-package.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'package build failed' }
}

$stata = $env:STATA_EXE
if (-not $stata) {
    $stata = Get-ChildItem 'C:\Program Files\Stata*\Stata*-64.exe' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '_old' } | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $stata -or -not (Test-Path $stata)) { throw 'Stata not found; set STATA_EXE to its executable.' }

$package = Join-Path $repo 'target\stata-package'
$env:CTOSIM_JAR = Join-Path $package 'ctosim.jar'
$env:CTOSIM_CONVERTER = Join-Path $package 'ctosim_converter.zip'
foreach ($f in $env:CTOSIM_JAR, $env:CTOSIM_CONVERTER) {
    if (-not (Test-Path $f)) { throw "$f is missing; run scripts/build-stata-package.ps1 (or -Build)." }
}

$testDir = if ($TestDir) { (Resolve-Path $TestDir).Path } else { Join-Path $repo 'src\test\stata' }
$tests = @(Get-ChildItem $testDir -Filter $Filter -File -ErrorAction SilentlyContinue | Sort-Object Name)
if ($tests.Count -eq 0) { Write-Warning "No tests matching $Filter in src/test/stata."; exit 0 }

# Test-support files that the tests build from source before Stata starts (Stata ignores shell in
# batch mode on Windows, so the tests cannot build them themselves): every build-*.ps1 next to them.
foreach ($fixtureBuild in Get-ChildItem $testDir -Filter 'build-*.ps1' -File | Sort-Object Name) {
    & $fixtureBuild.FullName
    if (-not $?) { throw "$($fixtureBuild.Name) failed" }
}

$root = Join-Path $repo 'target\stata-tests'
$failed = @()
foreach ($t in $tests) {
    $work = Join-Path $root $t.BaseName
    if (Test-Path $work) { Remove-Item -Recurse -Force $work }
    New-Item -ItemType Directory -Force $work | Out-Null
    $runner = @"
adopath ++ "$repo\stata"
global CTOSIM_REPO "$repo"
global CTOSIM_TESTDIR "$testDir"
cd "$work"
do "$($t.FullName)"
"@
    Set-Content -Path (Join-Path $work 'runner.do') -Value $runner -Encoding ascii
    Start-Process -FilePath $stata -ArgumentList '/e', 'do', 'runner.do' -WorkingDirectory $work -Wait -WindowStyle Hidden
    $log = Join-Path $work 'runner.log'
    $text = if (Test-Path $log) { Get-Content $log -Raw } else { '' }
    $failure = [regex]::Match($text, '(?m)^r\((\d+)\);\s*$')
    if (-not $text -or $failure.Success -or $text -notmatch 'end of do-file') {
        $code = if ($failure.Success) { "r($($failure.Groups[1].Value))" } else { 'no complete log' }
        Write-Host ("FAIL  {0}  {1}  (log: {2})" -f $t.Name, $code, $log)
        $failed += $t.Name
    } else {
        Write-Host ("ok    {0}" -f $t.Name)
    }
}
Write-Host ("{0} tests, {1} failed" -f $tests.Count, $failed.Count)
if ($failed.Count -gt 0) { exit 1 }
