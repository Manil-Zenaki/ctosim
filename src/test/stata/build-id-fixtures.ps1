$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
$support = Join-Path $repoPath '.codex-runs/stata-support'
$classes = Join-Path $support 'id-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') --release 8 -encoding UTF-8 -d $classes (Join-Path $PSScriptRoot 'fixtures/IdTestSupport.java') (Join-Path $PSScriptRoot 'fixtures/SimulatorDouble.java')
if ($LASTEXITCODE -ne 0) { throw 'Amendment 7 test support did not compile' }
& (Join-Path $env:JAVA_HOME 'bin/jar.exe') cfe (Join-Path $support 'id-fixture.jar') IdTestSupport -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Amendment 7 test fixture packaging failed' }
& (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp $classes IdTestSupport --self-check
if ($LASTEXITCODE -ne 0) { throw 'Amendment 7 help oracle checks failed' }
