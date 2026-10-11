# Rebuild the tiny Java bridge used because Windows Stata disables shell in batch.
# All compiler intermediates remain in .codex-runs. No production dependencies.
$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
$classes = Join-Path $repoPath '.codex-runs/stata-command/support-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') --release 8 -encoding UTF-8 -d $classes (Join-Path $PSScriptRoot 'fixtures/StataTestSupport.java')
if ($LASTEXITCODE -ne 0) { throw 'Test support compilation failed' }
& (Join-Path $env:JAVA_HOME 'bin/jar.exe') cf (Join-Path $PSScriptRoot 'fixtures/stata-test-support.jar') -C $classes StataTestSupport.class
if ($LASTEXITCODE -ne 0) { throw 'Test support jar creation failed' }
