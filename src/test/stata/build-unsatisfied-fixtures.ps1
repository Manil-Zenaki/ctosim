$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
$support = Join-Path $repoPath '.codex-runs/stata-support'
$classes = Join-Path $support 'unsatisfied-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') --release 8 -encoding UTF-8 -d $classes (Join-Path $PSScriptRoot 'fixtures/UnsatisfiedFixture.java') (Join-Path $PSScriptRoot 'fixtures/UnsatisfiedOracleChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Unsatisfied-constraint fixtures did not compile' }
& (Join-Path $env:JAVA_HOME 'bin/jar.exe') cfe (Join-Path $support 'unsatisfied-fixture.jar') UnsatisfiedFixture -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Unsatisfied-constraint fixture packaging failed' }
