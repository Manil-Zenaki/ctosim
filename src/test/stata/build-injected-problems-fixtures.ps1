$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
$support = Join-Path $repoPath '.codex-runs/stata-support'
$classes = Join-Path $support 'injected-problems-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') --release 8 -encoding UTF-8 -d $classes (Join-Path $PSScriptRoot 'fixtures/InjectedProblemsFixture.java') (Join-Path $PSScriptRoot 'fixtures/SimulatorDouble.java')
if ($LASTEXITCODE -ne 0) { throw 'Amendment 8 fixture compilation failed' }
& (Join-Path $env:JAVA_HOME 'bin/jar.exe') cfe (Join-Path $support 'injected-problems-fixture.jar') InjectedProblemsFixture -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Amendment 8 fixture packaging failed' }
& (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp $classes InjectedProblemsFixture --self-check
if ($LASTEXITCODE -ne 0) { throw 'Amendment 8 oracle checks failed' }
