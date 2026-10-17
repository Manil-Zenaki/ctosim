$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
$support = Join-Path $repoPath '.codex-runs/stata-support'
$classes = Join-Path $support 'fakepreload-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') --release 8 -encoding UTF-8 -d $classes (Join-Path $PSScriptRoot 'fixtures/FakepreloadFixture.java')
if ($LASTEXITCODE -ne 0) { throw 'Fakepreload fixtures did not compile' }
& (Join-Path $env:JAVA_HOME 'bin/jar.exe') cfe (Join-Path $support 'fakepreload-fixture.jar') FakepreloadFixture -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Fakepreload fixture packaging failed' }
& (Join-Path $env:JAVA_HOME 'bin/java.exe') -cp $classes FakepreloadFixture --check-oracle (Join-Path $support 'fakepreload-oracle')
if ($LASTEXITCODE -ne 0) { throw 'Fakepreload display oracle self-check failed' }
