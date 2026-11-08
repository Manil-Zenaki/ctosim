$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
$support = Join-Path $repoPath '.codex-runs/stata-support'
$classes = Join-Path $support 'revisit-answers-help-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') --release 8 -encoding UTF-8 -d $classes (Join-Path $PSScriptRoot 'fixtures/RevisitAnswersHelpChecks.java')
if ($LASTEXITCODE -ne 0) { throw 'Amendment 20 help checks did not compile' }
& (Join-Path $env:JAVA_HOME 'bin/jar.exe') cf (Join-Path $support 'revisit-answers-help.jar') -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Amendment 20 help checks could not be packaged' }
& (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp $classes RevisitAnswersHelpChecks
if ($LASTEXITCODE -ne 0) { throw 'Amendment 20 help oracle self-check failed' }
