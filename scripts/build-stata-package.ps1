<#
.SYNOPSIS
  Builds the Stata package in target/stata-package (spec 11, "Packaging").

.DESCRIPTION
  Builds the simulator (mvnw package), then gathers:
    ctosim.ado, ctosim.sthlp, ctosim.pkg, stata.toc   from stata/
    ctosim.jar                                         target/ctosim-standalone.jar
    ctosim_converter.zip                               the converter and its pure-Python libraries
  The converter's libraries are taken from the project's .venv.

.EXAMPLE
  .\scripts\build-stata-package.ps1
  .\scripts\build-stata-package.ps1 -SkipBuild     # reuse target/ctosim-standalone.jar
#>
param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = 'C:\Program Files\StataNow19\utilities\java\windows-x64\zulu-jdk21.0.11'
}
$env:MAVEN_USER_HOME = Join-Path $repo '.tools\maven-home'

if (-not $SkipBuild) {
    & .\mvnw.cmd -o -q package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "mvnw package failed ($LASTEXITCODE)" }
}

$out = Join-Path $repo 'target\stata-package'
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force $out | Out-Null

foreach ($f in 'ctosim.ado', 'ctosim.sthlp', 'ctosim.pkg', 'stata.toc') {
    $src = Join-Path $repo "stata\$f"
    if (Test-Path $src) { Copy-Item $src $out } else { Write-Warning "missing stata\$f" }
}
Copy-Item (Join-Path $repo 'target\ctosim-standalone.jar') (Join-Path $out 'ctosim.jar')

# The converter and its libraries (all pure Python), importable from one zip archive.
$stage = Join-Path $repo 'target\converter-stage'
if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
New-Item -ItemType Directory -Force $stage | Out-Null
$sitePackages = Join-Path $repo '.venv\Lib\site-packages'
foreach ($p in 'pyxform', 'openpyxl', 'et_xmlfile', 'xlrd', 'defusedxml', 'lark') {
    Copy-Item -Recurse (Join-Path $sitePackages $p) $stage
}
Copy-Item -Recurse (Join-Path $repo 'converter\src\ctosim_xlsform') $stage
Get-ChildItem $stage -Recurse -Directory -Filter __pycache__ | Remove-Item -Recurse -Force
# Python writes the archive: Compress-Archive (Windows PowerShell 5.1) stores backslashes in
# entry names, which Python cannot import from.
$python = Join-Path $repo '.venv\Scripts\python.exe'
& $python -I -c "import shutil, sys; shutil.make_archive(sys.argv[1], 'zip', sys.argv[2])" `
    (Join-Path $out 'ctosim_converter') $stage
if ($LASTEXITCODE -ne 0) { throw "could not build ctosim_converter.zip" }
Remove-Item -Recurse -Force $stage

Write-Host "Stata package: $out"
Get-ChildItem $out | ForEach-Object { Write-Host ("  {0,-24} {1,12:N0} bytes" -f $_.Name, $_.Length) }
