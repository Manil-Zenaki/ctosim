param([Parameter(Mandatory=$true)][string]$Repo,
      [Parameter(Mandatory=$true)][string]$Test,
      [Parameter(Mandatory=$true)][string]$Profile)
$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath $Repo).Path
if ($Test -notmatch '^test_converter_name_[a-z_]+$' -or $Profile -notmatch '^[a-z_]+$') { throw 'Invalid test/profile' }
$scratch = Join-Path $repoPath '.codex-runs/stata-converter-name'
$work = [IO.Path]::GetFullPath((Join-Path $scratch "$Test-$Profile"))
$allowed = [IO.Path]::GetFullPath($scratch).TrimEnd('\') + '\'
if (-not $work.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe test directory' }
if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force }
New-Item -ItemType Directory -Path $work -Force | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)
$testRoot = Join-Path $repoPath 'src/test/stata'
$archives = Join-Path $repoPath '.codex-runs/stata-support/converter-name-archives'
$early = Join-Path $work 'first ado directory'
$late = Join-Path $work 'second ado directory'
$explicit = Join-Path $work 'explicit files'
$empty = Join-Path $work 'empty ado directory'
foreach ($directory in @($early, $late, $explicit, $empty, (Join-Path $work 'temp'))) {
    New-Item -ItemType Directory -Path $directory | Out-Null
}
$env:TEMP = Join-Path $work 'temp'
$env:TMP = $env:TEMP
$env:CTOSIM_JAR = Join-Path $repoPath 'target/stata-package/ctosim.jar'
$env:CTOSIM_JAVA = Join-Path $env:JAVA_HOME 'bin/java.exe'
$python = Join-Path $repoPath '.venv/Scripts/python.exe'
$env:CTOSIM_PYTHON = $python
# Prevent ambient Python paths from masking an archive that was not made importable.
Remove-Item Env:PYTHONPATH -ErrorAction SilentlyContinue
$env:CTOSIM_TEST_WORK = $work
$env:CTOSIM_TEST_FIXTURES = Join-Path $testRoot 'fixtures'
Remove-Item Env:CTOSIM_CONVERTER -ErrorAction SilentlyContinue
$expected = 'jar'
function Add-Archive([string]$identity, [string]$directory, [string]$name) {
    Copy-Item -LiteralPath (Join-Path $archives "$identity.jar") -Destination (Join-Path $directory $name)
}
switch ($Profile) {
    'jar_unset' { Add-Archive 'jar' $late 'ctosim_converter.jar' }
    'jar_empty' { Add-Archive 'jar' $late 'ctosim_converter.jar'; $env:CTOSIM_CONVERTER = '' }
    'real_jar' {
        Copy-Item -LiteralPath (Join-Path $repoPath 'target/stata-package/ctosim_converter.zip') -Destination (Join-Path $late 'ctosim_converter.jar')
        $expected = 'real'
    }
    'zip_only' { Add-Archive 'zip' $late 'ctosim_converter.zip'; $expected = 'zip' }
    'zip_both' {
        Add-Archive 'jar' $late 'ctosim_converter.jar'
        Add-Archive 'zip' $late 'ctosim_converter.zip'; $expected = 'zip'
    }
    'zip_later' {
        Add-Archive 'jar' $early 'ctosim_converter.jar'
        Add-Archive 'zip' $late 'ctosim_converter.zip'; $expected = 'zip'
    }
    { $_ -in 'override_jar', 'override_zip', 'override_other', 'override_relative' } {
        Add-Archive 'jar' $early 'ctosim_converter.jar'
        Add-Archive 'zip' $late 'ctosim_converter.zip'
        $name = switch ($Profile) {
            'override_jar' { 'custom converter.jar' }
            'override_zip' { 'custom converter.zip' }
            default { 'custom converter.archive' }
        }
        Add-Archive 'override' $explicit $name
        $env:CTOSIM_CONVERTER = Join-Path $explicit $name
        if ($Profile -eq 'override_relative') { $env:CTOSIM_CONVERTER = "explicit files/$name" }
        $expected = 'override'
    }
    'missing_unset' { $expected = 'missing' }
    'missing_empty' { $env:CTOSIM_CONVERTER = ''; $expected = 'missing' }
    'missing_override' {
        Add-Archive 'jar' $early 'ctosim_converter.jar'
        Add-Archive 'zip' $late 'ctosim_converter.zip'
        $env:CTOSIM_CONVERTER = Join-Path $explicit 'absent converter.jar'; $expected = 'missing'
    }
    'xml_missing' { $expected = 'xml' }
    'xml_invalid_override' {
        Add-Archive 'jar' $early 'ctosim_converter.jar'
        Add-Archive 'zip' $late 'ctosim_converter.zip'
        $env:CTOSIM_CONVERTER = Join-Path $explicit 'absent converter.jar'; $expected = 'xml'
    }
    default { throw "Unknown profile $Profile" }
}
if ($expected -eq 'missing') {
    $env:CTOSIM_JAR = Join-Path $repoPath '.codex-runs/stata-support/converter-name-simulator.jar'
}
$stata = $env:STATA_EXE
if (-not $stata) {
    $stata = Get-ChildItem 'C:\Program Files\Stata*\Stata*-64.exe' |
        Where-Object Name -NotMatch '_old' | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $stata) { throw 'Stata unavailable' }
$normalRepo = $repoPath.Replace('\', '/')
$normalWork = $work.Replace('\', '/')
$driver = @"
version 16.0
set more off
set linesize 255
// Isolate user-installed converters while retaining Stata's official commands.
sysdir set SITE "$empty"
sysdir set PLUS "$empty"
sysdir set PERSONAL "$empty"
sysdir set OLDPLACE "$empty"
adopath ++ "$normalRepo/stata"
adopath ++ "$late"
adopath ++ "$early"
global CTOSIM_REPO "$normalRepo"
global CTOSIM_TESTDIR "$($testRoot.Replace('\', '/'))"
global CT_TEST_WORK "$normalWork"
global CT_CONVERTER_EXPECT "$expected"
python set exec "$python"
cd "$normalWork"
do "$($testRoot.Replace('\', '/'))/$Test.do" child
file open completed using "completed.txt", write text replace
file write completed "passed" _n
file close completed
exit, clear
"@
[IO.File]::WriteAllText((Join-Path $work 'child.do'), $driver, $utf8)
$process = Start-Process -FilePath $stata -ArgumentList '/e', 'do', 'child.do' -WorkingDirectory $work -WindowStyle Hidden -PassThru
if (-not $process.WaitForExit(60000)) { Stop-Process -Id $process.Id -Force; throw 'Child exceeded one minute' }
if (-not (Test-Path -LiteralPath (Join-Path $work 'completed.txt'))) {
    Get-Content -LiteralPath (Join-Path $work 'child.log')
    throw 'Child did not complete successfully'
}
Write-Output "PASS $Test $Profile"
