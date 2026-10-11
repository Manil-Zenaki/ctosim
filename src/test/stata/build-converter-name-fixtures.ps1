$ErrorActionPreference = 'Stop'
$repoPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../..')).Path
$support = Join-Path $repoPath '.codex-runs/stata-support'
$classes = Join-Path $support 'converter-name-classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') --release 8 -d $classes (Join-Path $PSScriptRoot 'fixtures/ConverterNameTestSupport.java')
if ($LASTEXITCODE -ne 0) { throw 'Converter-name bridge compilation failed' }
& (Join-Path $env:JAVA_HOME 'bin/jar.exe') cf (Join-Path $support 'converter-name-test-support.jar') -C $classes ConverterNameTestSupport.class
if ($LASTEXITCODE -ne 0) { throw 'Converter-name bridge packaging failed' }
& (Join-Path $repoPath '.venv/Scripts/python.exe') (Join-Path $PSScriptRoot 'fixtures/build_converter_name_archives.py') (Join-Path $support 'converter-name-archives')
if ($LASTEXITCODE -ne 0) { throw 'Converter-name archive fixture creation failed' }
& (Join-Path $env:JAVA_HOME 'bin/javac.exe') --release 8 -encoding UTF-8 -d $classes (Join-Path $PSScriptRoot 'fixtures/SimulatorDouble.java')
if ($LASTEXITCODE -ne 0) { throw 'Simulator double compilation failed' }
& (Join-Path $env:JAVA_HOME 'bin/jar.exe') cfe (Join-Path $support 'converter-name-simulator.jar') SimulatorDouble -C $classes SimulatorDouble.class
if ($LASTEXITCODE -ne 0) { throw 'Simulator double packaging failed' }
