<#
.SYNOPSIS
  Runs Codex in a fresh session to write (or adjudicate) the unit tests for one spec,
  then commits the result with a Test-Author trailer.

.EXAMPLE
  .\scripts\codex-tests.ps1 specs\01-form-loader.md
  .\scripts\codex-tests.ps1 specs\01-form-loader.md -Dispute
#>
param(
    [Parameter(Mandatory = $true)][string]$Spec,
    [switch]$Dispute,
    [switch]$NoCommit,
    # Restricts a run to one part of the spec, e.g. a newly added amendment.
    [string]$Focus
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

# --- Locate tools -----------------------------------------------------------
$codex = Get-Command codex -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $codex) {
    $codex = Get-ChildItem "$env:USERPROFILE\.vscode\extensions\openai.chatgpt-*\bin\windows-x86_64\codex.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $codex) { throw 'Codex CLI not found (install it or the ChatGPT VS Code extension).' }

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = 'C:\Program Files\StataNow19\utilities\java\windows-x64\zulu-jdk21.0.11'
}

# --- Preconditions ----------------------------------------------------------
$specPath = (Resolve-Path $Spec).Path
$specRel = (Resolve-Path -Relative $specPath) -replace '^\.\\', '' -replace '\\', '/'
$specBase = [IO.Path]::GetFileNameWithoutExtension($specPath)          # e.g. 01-form-loader
$module = $specBase -replace '^\d+-', ''                               # e.g. form-loader
$questions = "specs/$specBase.questions.md"
$disputes = "specs/$specBase.disputes.md"

if (git status --porcelain) { throw 'Working tree is not clean; commit or stash first.' }
if ($Dispute -and -not (Test-Path $disputes)) { throw "No disputes file: $disputes" }

# --- Prompt -----------------------------------------------------------------
$isPython = (Get-Content $specPath -Raw) -match '(?m)^Language:\s*Python'
$testDir = if ($isPython) { 'converter/tests' } else { 'src/test' }
$mainDir = if ($isPython) { 'converter/src' } else { 'src/main' }

if ($Dispute) {
    $prompt = @"
Read AGENTS.md. You are adjudicating a dispute about the tests for the spec $specRel.
Read the spec, then $disputes, then the disputed tests under $testDir/.
Decide strictly on the spec. If a test is wrong, fix it. If it is right, keep it.
Append your decision and reasoning for each disputed point at the end of $disputes.
Run the tests to make sure they compile or import. Do not read implementation code under $mainDir/
beyond the public signatures.
"@
} elseif ($isPython) {
    $prompt = @"
Read AGENTS.md, then the spec $specRel. Write comprehensive pytest tests for this module
under converter/tests (fixtures under converter/tests/fixtures). Record ambiguities and the
assumptions you made in $questions. Make sure the tests are collected without errors with:
.venv\Scripts\python -m pytest converter/tests -q --collect-only
(failing tests are expected because the module is not implemented yet).
"@
} else {
    $prompt = @"
Read AGENTS.md, then the spec $specRel. Write comprehensive JUnit 5 tests for this module
under src/test/java (and fixtures under src/test/resources). Record ambiguities and the
assumptions you made in $questions. Make sure the tests compile with: mvnw.cmd -o -q test-compile
(failing tests are expected because the module is not implemented yet).
"@
    if ($Focus) {
        $prompt += @"

Existing tests for this spec are already committed and must be kept. In this run, add tests
only for this part of the spec: $Focus. Put them in new test classes; do not rewrite existing ones.
"@
    }
}

# --- Run Codex --------------------------------------------------------------
New-Item -ItemType Directory -Force .codex-runs | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$log = ".codex-runs/$stamp-$specBase$(if ($Dispute) { '-dispute' }).log"
$last = ".codex-runs/$stamp-$specBase-last.md"

Write-Host "Running Codex (fresh session) on $specRel ..."
# Codex writes progress to stderr; in Windows PowerShell 5.1 that must not be fatal.
$ErrorActionPreference = 'Continue'
$prompt | & $codex exec --sandbox workspace-write --cd $repo --output-last-message $last - 2>&1 |
    ForEach-Object { "$_" } | Tee-Object -FilePath $log | Out-Null
$codexExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($codexExit -ne 0) { throw "Codex exited with code $codexExit; see $log" }

$session = (Select-String -Path $log -Pattern 'session id:\s*(\S+)' | Select-Object -First 1).Matches.Groups[1].Value

# --- Enforce Codex's write scope --------------------------------------------
$allowed = '^(src/test/|converter/tests/|' + [regex]::Escape($questions) + '$|' + [regex]::Escape($disputes) + '$)'
$changed = @(git status --porcelain --untracked-files=all | ForEach-Object { $_.Substring(3) -replace '"', '' })
$outOfScope = @($changed | Where-Object { $_ -notmatch $allowed })
if ($outOfScope.Count -gt 0) {
    Write-Warning "Codex changed files outside its scope; reverting them:`n  $($outOfScope -join "`n  ")"
    foreach ($f in $outOfScope) {
        git ls-files --error-unmatch -- $f 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { git checkout -- $f } else { Remove-Item -Force -Recurse $f }
    }
}

$inScope = @($changed | Where-Object { $_ -match $allowed })
if ($inScope.Count -eq 0) { Write-Warning 'Codex produced no test changes.'; exit 1 }

# --- Commit -----------------------------------------------------------------
if ($NoCommit) { Write-Host "Changes left uncommitted. Transcript: $log"; exit 0 }

git add -- $inScope
$subject = if ($Dispute) { "test($module): adjudicate disputed tests" }
           elseif ($Focus) { "test($module): add tests for $Focus" }
           else { "test($module): add unit tests from spec" }
$body = "Written by Codex in a fresh session from $specRel only.`n`nTest-Author: codex`nCodex-Session: $session"
git commit -q -m $subject -m $body
if ($LASTEXITCODE -ne 0) { throw 'git commit failed.' }
Write-Host "Committed: $subject (session $session). Transcript: $log"
