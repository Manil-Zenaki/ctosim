$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
$support = Join-Path $repoPath '.codex-runs/stata-support'
$classes = Join-Path $support 'form-problems-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') --release 8 -encoding UTF-8 -d $classes (Join-Path $PSScriptRoot 'fixtures/FormProblemsFixture.java') (Join-Path $PSScriptRoot 'fixtures/FormProblemsOracleChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Amendment 6 fixtures did not compile' }
& (Join-Path $env:JAVA_HOME 'bin/jar.exe') cfe (Join-Path $support 'form-problems-fixture.jar') FormProblemsFixture -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Amendment 6 fixture packaging failed' }
& (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp $classes FormProblemsOracleChecks (Join-Path $support 'form-problems-oracle')
if ($LASTEXITCODE -ne 0) { throw 'Amendment 6 oracle self-check failed' }
