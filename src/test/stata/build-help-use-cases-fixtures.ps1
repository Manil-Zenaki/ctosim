$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
$support = Join-Path $repoPath '.codex-runs/stata-support'
$classes = Join-Path $support 'help-use-cases-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') --release 8 -d $classes (Join-Path $PSScriptRoot 'fixtures/HelpUseCasesTestSupport.java')
if ($LASTEXITCODE -ne 0) { throw 'Help use-case bridge compilation failed' }
& (Join-Path $env:JAVA_HOME 'bin/jar.exe') cf (Join-Path $support 'help-use-cases-test-support.jar') -C $classes HelpUseCasesTestSupport.class
if ($LASTEXITCODE -ne 0) { throw 'Help use-case bridge packaging failed' }
