# Rebuild only the new bridge; all intermediates remain under .codex-runs.
$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
$classes = Join-Path $repoPath '.codex-runs/stata-amendment/classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') --release 8 -d $classes (Join-Path $PSScriptRoot 'fixtures/AmendmentTestSupport.java')
if ($LASTEXITCODE -ne 0) { throw 'Amendment bridge compilation failed' }
& (Join-Path $env:JAVA_HOME 'bin/jar.exe') cf (Join-Path $PSScriptRoot 'fixtures/amendment-test-support.jar') -C $classes AmendmentTestSupport.class
if ($LASTEXITCODE -ne 0) { throw 'Amendment bridge packaging failed' }
