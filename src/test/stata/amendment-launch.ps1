param([Parameter(Mandatory=$true)][string]$Repo,
      [Parameter(Mandatory=$true)][string]$Test,
      [Parameter(Mandatory=$true)][string]$Profile)
$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath $Repo).Path
if ($Test -notmatch '^test_amendment_[a-z_]+$' -or $Profile -notmatch '^[a-z0-9_]+$') { throw 'Invalid test/profile' }
$testRoot = Join-Path $repoPath 'src/test/stata'
$scratch = Join-Path $repoPath '.codex-runs/stata-amendment'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$realHome = $env:JAVA_HOME
$realJava = Join-Path $realHome 'bin/java.exe'
$statas = @($env:STATA_EXE)
if (-not $env:STATA_EXE) {
    $statas = @(Get-ChildItem 'C:\Program Files\Stata*\Stata*-64.exe' |
        Where-Object Name -NotMatch '_old' | Select-Object -ExpandProperty FullName)
}
if (-not $statas.Count) { throw 'Stata unavailable' }
$failed = $false
foreach ($stata in $statas) {
    $edition = Split-Path -Leaf (Split-Path -Parent $stata)
    $work = Join-Path $scratch "$Test-$Profile-$edition"
    $resolvedWork = [IO.Path]::GetFullPath($work)
    $allowed = [IO.Path]::GetFullPath($scratch).TrimEnd('\') + '\'
    if (-not $resolvedWork.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe scratch directory' }
    if (Test-Path -LiteralPath $resolvedWork) { Remove-Item -LiteralPath $resolvedWork -Recurse -Force }
    New-Item -ItemType Directory -Path $work -Force | Out-Null
    try {
        $temporary = Join-Path $work 'temp'
        New-Item -ItemType Directory -Path $temporary | Out-Null
        $env:TEMP = $temporary
        $env:TMP = $temporary
        $programs = Join-Path $work 'program files'
        New-Item -ItemType Directory -Path $programs | Out-Null
        # Real PE launchers work whether ctosim uses Java, Python, or Windows process APIs.
        $launcher = Join-Path $programs 'launcher.exe'
        Add-Type -Path (Join-Path $testRoot 'fixtures/AmendmentJavaLauncher.cs') -OutputAssembly $launcher -OutputType ConsoleApplication
        & (Join-Path $realHome 'bin/javac.exe') --release 8 -encoding UTF-8 -d $programs (Join-Path $testRoot 'fixtures/SimulatorDouble.java')
        if ($LASTEXITCODE -ne 0) { throw 'Simulator double compilation failed' }
        $jar = Join-Path $programs 'simulator double.jar'
        & (Join-Path $realHome 'bin/jar.exe') cfe $jar SimulatorDouble -C $programs SimulatorDouble.class
        if ($LASTEXITCODE -ne 0) { throw 'Simulator double packaging failed' }
        $executables = @{}
        function New-JavaSlot([string]$slot, [string]$version, [string]$stream = 'stderr') {
            $directory = Join-Path $programs "$slot runtime/bin"
            New-Item -ItemType Directory -Path $directory -Force | Out-Null
            $exe = Join-Path $directory 'java.exe'
            Copy-Item -LiteralPath $launcher -Destination $exe
            [IO.File]::WriteAllLines("$exe.settings", @($version, $realJava, $work, $slot, $stream), $utf8)
            $executables[$slot] = $exe
        }
        New-JavaSlot 'override' '17.0.1'
        New-JavaSlot 'home' '21.0.1'
        New-JavaSlot 'path' '25.0.1'
        $env:CTOSIM_JAVA = $executables.override
        $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $executables.home)
        # Keep Windows tools available, but exclude every ambient Java from PATH.
        $env:PATH = (Split-Path -Parent $executables.path) + ";$env:SystemRoot\System32;$env:SystemRoot;$env:SystemRoot\System32\WindowsPowerShell\v1.0"
        $expected = 'override'
        switch ($Profile) {
            'override_17' { New-JavaSlot 'override' '17' }
            'override_21_stdout' { New-JavaSlot 'override' '21.0.2' 'stdout' }
            'override_25' { New-JavaSlot 'override' '25.0.1' }
            'override_8' { New-JavaSlot 'override' '1.8.0_412'; $expected = 'own_or_home' }
            'override_11' { New-JavaSlot 'override' '11.0.22'; $expected = 'own_or_home' }
            'override_16' { New-JavaSlot 'override' '16.0.2'; $expected = 'own_or_home' }
            'override_missing' { $env:CTOSIM_JAVA = Join-Path $programs 'absent java.exe'; $expected = 'own_or_home' }
            'override_empty' { $env:CTOSIM_JAVA = ''; $expected = 'own_or_home' }
            'override_unset' { Remove-Item Env:CTOSIM_JAVA -ErrorAction SilentlyContinue; $expected = 'own_or_home' }
            'home_17' { $env:CTOSIM_JAVA = ''; New-JavaSlot 'home' '17.0.1'; $expected = 'own_or_home' }
            'home_16' { $env:CTOSIM_JAVA = ''; New-JavaSlot 'home' '16.0.2'; $expected = 'own_or_path' }
            'home_missing' { $env:CTOSIM_JAVA = ''; $env:JAVA_HOME = Join-Path $programs 'absent home'; $expected = 'own_or_path' }
            'home_empty' { $env:CTOSIM_JAVA = ''; $env:JAVA_HOME = ''; $expected = 'own_or_path' }
            'home_unset' { Remove-Item Env:CTOSIM_JAVA, Env:JAVA_HOME -ErrorAction SilentlyContinue; $expected = 'own_or_path' }
            'path_17' { $env:CTOSIM_JAVA = ''; $env:JAVA_HOME = ''; New-JavaSlot 'path' '17'; $expected = 'own_or_path' }
            'none_old' {
                New-JavaSlot 'override' '16.0.2'; New-JavaSlot 'home' '11.0.22'; New-JavaSlot 'path' '1.8.0_412'
                $expected = 'none'
            }
            'none_missing' {
                $env:CTOSIM_JAVA = Join-Path $programs 'absent java.exe'
                $env:JAVA_HOME = Join-Path $programs 'absent home'
                Remove-Item -LiteralPath $executables.path -Force
                $expected = 'none'
            }
            'none_empty' {
                $env:CTOSIM_JAVA = ''; $env:JAVA_HOME = ''
                Remove-Item -LiteralPath $executables.path -Force
                $expected = 'none'
            }
            'batch' { }
            default { throw "Unknown profile $Profile" }
        }
        $env:CTOSIM_JAR = $jar
        $env:CTOSIM_CONVERTER = Join-Path $repoPath 'target/stata-package/ctosim_converter.zip'
        $env:CTOSIM_TEST_WORK = $work
        $env:CTOSIM_TEST_FIXTURES = Join-Path $testRoot 'fixtures'
        $normalWork = $work.Replace('\', '/')
        $normalTests = $testRoot.Replace('\', '/')
        $driver = @"
version 16.0
set more off
set linesize 255
adopath ++ "$($repoPath.Replace('\', '/'))/stata"
adopath ++ "$normalTests/fixtures"
global CTOSIM_REPO "$($repoPath.Replace('\', '/'))"
global CTOSIM_TESTDIR "$normalTests"
global CT_TEST_WORK "$normalWork"
global CT_AMENDMENT_EXPECT "$expected"
cd "$normalWork"
do "$normalTests/$Test.do" child
file open completed using "completed.txt", write text replace
file write completed "passed" _n
file close completed
exit, clear
"@
        [IO.File]::WriteAllText((Join-Path $work 'child.do'), $driver, $utf8)
        $proc = Start-Process -FilePath $stata -ArgumentList '/e', 'do', 'child.do' -WorkingDirectory $work -WindowStyle Hidden -PassThru
        if (-not $proc.WaitForExit(60000)) { Stop-Process -Id $proc.Id -Force; throw 'Child exceeded one minute' }
        $log = Get-Content -LiteralPath (Join-Path $work 'child.log') -Raw
        # Only an uncaptured error prevents the completion sentinel. Expected
        # errors caught and asserted by a test must not be mistaken for failures.
        if (-not $log -or -not (Test-Path -LiteralPath (Join-Path $work 'completed.txt'))) {
            throw "Child failed: $work/child.log`n$log"
        }
        Write-Output "PASS $Test $Profile $edition"
    } catch {
        $failed = $true
        Write-Output $_.ToString()
    }
}
if ($failed) { exit 1 }
