param([Parameter(Mandatory=$true)][string]$Repo,
      [Parameter(Mandatory=$true)][string]$Test,
      [string]$Profile = 'double')
$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath $Repo).Path
$testRoot = Join-Path $repoPath 'src/test/stata'
$scratch = Join-Path $repoPath '.codex-runs/stata-command'
$work = Join-Path $scratch $Test
$passed = Join-Path $scratch "$Test.passed"
$utf8 = New-Object System.Text.UTF8Encoding($false)
try {
    if ($Test -notmatch '^test_[a-z0-9_]+$') { throw 'Invalid test name' }
    New-Item -ItemType Directory -Force -Path $scratch | Out-Null
    # Check containment before cleaning this test's own work directory.
    $resolvedWork = [IO.Path]::GetFullPath($work)
    $allowed = [IO.Path]::GetFullPath($scratch).TrimEnd('\') + '\'
    if (-not $resolvedWork.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Work directory escaped scratch root'
    }
    if (Test-Path -LiteralPath $resolvedWork) { Remove-Item -LiteralPath $resolvedWork -Recurse -Force }
    if (Test-Path -LiteralPath $passed) { Remove-Item -LiteralPath $passed -Force }
    New-Item -ItemType Directory -Path $work | Out-Null
    $stata = $env:STATA_EXE
    if (-not $stata) {
        $stata = Get-ChildItem 'C:\Program Files\Stata*\Stata*-64.exe' |
            Where-Object Name -NotMatch '_old' | Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $stata -or -not (Test-Path -LiteralPath $stata)) { throw 'Stata executable unavailable' }
    $python = Join-Path $repoPath '.venv/Scripts/python.exe'
    $jar = Join-Path $repoPath 'target/stata-package/ctosim.jar'
    $converter = Join-Path $repoPath 'target/stata-package/ctosim_converter.zip'
    $fakeDir = Join-Path $work 'program files'
    New-Item -ItemType Directory -Path $fakeDir | Out-Null
    if ($Profile -notlike 'real*') {
        $javac = Join-Path $env:JAVA_HOME 'bin/javac.exe'
        $jarTool = Join-Path $env:JAVA_HOME 'bin/jar.exe'
        & $javac --release 8 -encoding UTF-8 -d $fakeDir (Join-Path $testRoot 'fixtures/SimulatorDouble.java')
        if ($LASTEXITCODE -ne 0) { throw 'Simulator test double did not compile' }
        $jar = Join-Path $fakeDir 'ctosim.jar'
        & $jarTool cfe $jar SimulatorDouble -C $fakeDir SimulatorDouble.class
        if ($LASTEXITCODE -ne 0) { throw 'Could not package simulator test double' }
    }
    $env:CTOSIM_JAR = $jar
    $env:CTOSIM_CONVERTER = $converter
    $env:CTOSIM_PYTHON = $python
    $env:CTOSIM_TEST_WORK = $work
    $env:CTOSIM_TEST_FIXTURES = Join-Path $testRoot 'fixtures'
    $setup = @()
    # Each process has its own environment; nothing below changes the user's settings.
    # Querying Python does not initialize its embedded runtime.
    Copy-Item -LiteralPath $converter -Destination (Join-Path $fakeDir 'ctosim_converter.zip')
    $setup += "adopath ++ `"$fakeDir`""
    switch ($Profile) {
        'missing_jar' { $env:CTOSIM_JAR = Join-Path $work 'missing simulator.jar' }
        'missing_converter' { $env:CTOSIM_CONVERTER = Join-Path $work 'missing converter.zip' }
        'missing_python' {
            # Configure a real venv launcher, then remove only that disposable copy.
            $venv = Join-Path $work 'python env'
            New-Item -ItemType Directory -Path (Join-Path $venv 'Scripts') | Out-Null
            Copy-Item -LiteralPath $python -Destination (Join-Path $venv 'Scripts/python.exe')
            Copy-Item -LiteralPath (Join-Path $repoPath '.venv/pyvenv.cfg') -Destination $venv
            $gone = (Join-Path $venv 'Scripts/python.exe').Replace('\', '/')
            $setup += "python set exec `"$gone`""
            $setup += "erase `"$gone`""
            $env:CTOSIM_PYTHON = ''
        }
        'xml_without_python' {
            $env:CTOSIM_PYTHON = Join-Path $work 'missing python.exe'
            $env:CTOSIM_CONVERTER = Join-Path $work 'missing converter.zip'
        }
        'ado_fallback' {
            $env:CTOSIM_JAR = ''
            $env:CTOSIM_CONVERTER = ''
        }
        'ado_unset' {
            Remove-Item Env:CTOSIM_JAR -ErrorAction SilentlyContinue
            Remove-Item Env:CTOSIM_CONVERTER -ErrorAction SilentlyContinue
        }
        'configured_python' {
            $setup += "python set exec `"$python`""
            $env:CTOSIM_PYTHON = Join-Path $work 'must not use this python.exe'
        }
        'real_configured_python' {
            $setup += "python set exec `"$python`""
            $env:CTOSIM_PYTHON = Join-Path $work 'must not use this python.exe'
        }
    }
    $normalRepo = $repoPath.Replace('\', '/')
    $normalTests = $testRoot.Replace('\', '/')
    $normalWork = $work.Replace('\', '/')
    $driver = @"
version 16.0
set more off
set linesize 255
adopath ++ "$normalRepo/stata"
global CTOSIM_REPO "$normalRepo"
global CTOSIM_TESTDIR "$normalTests"
global CT_TEST_WORK "$normalWork"
global CT_TEST_PYTHON "$($python.Replace('\', '/'))"
$($setup -join "`n")
cd "$normalWork"
do "$normalTests/$Test.do" child
file open completed using "$normalWork/completed.txt", write text replace
file write completed "completed" _n
file close completed
exit, clear
"@
    [IO.File]::WriteAllText((Join-Path $work 'child.do'), $driver, $utf8)
    $proc = Start-Process -FilePath $stata -ArgumentList '/e', 'do', 'child.do' -WorkingDirectory $work -WindowStyle Hidden -PassThru
    if (-not $proc.WaitForExit(180000)) {
        Stop-Process -Id $proc.Id -Force
        throw 'Child Stata exceeded three minutes'
    }
    $logPath = Join-Path $work 'child.log'
    $log = if (Test-Path -LiteralPath $logPath) { Get-Content -LiteralPath $logPath -Raw } else { '' }
    if (-not (Test-Path -LiteralPath (Join-Path $work 'completed.txt'))) {
        throw "Test did not reach completion.`r`n$log"
    }
    [IO.File]::WriteAllText($passed, 'passed', $utf8)
} catch {
    [IO.File]::WriteAllText((Join-Path $scratch "$Test.failure.txt"), $_.ToString(), $utf8)
    Write-Error $_
    exit 1
}
