<#
.SYNOPSIS
  Commits the built Stata package to the branch stata-package (spec 11, "Packaging").

.DESCRIPTION
  Builds target/stata-package (unless -SkipBuild), then replaces the content of the branch
  stata-package with it, in a temporary git worktree, and commits. The branch holds only the
  package files; main never holds build output. Pushing is left to the caller:
      git push origin stata-package
  Users then install with:
      net install ctosim, from("https://raw.githubusercontent.com/<owner>/<repo>/stata-package/") replace

.EXAMPLE
  .\scripts\publish-stata-package.ps1
#>
param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

if (git status --porcelain) { throw 'Working tree is not clean; commit first, so the package matches a commit.' }
if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot 'build-stata-package.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'package build failed' }
}
$package = Join-Path $repo 'target\stata-package'
$source = (git rev-parse --short HEAD).Trim()

$worktree = Join-Path $repo 'target\stata-package-branch'
if (Test-Path $worktree) {
    git worktree remove --force $worktree 2>$null
    if (Test-Path $worktree) { Remove-Item -Recurse -Force $worktree }
}
git show-ref --verify --quiet refs/heads/stata-package
if ($LASTEXITCODE -eq 0) {
    git worktree add $worktree stata-package | Out-Null
} else {
    git worktree add --detach $worktree | Out-Null
    git -C $worktree checkout --orphan stata-package | Out-Null
}
if ($LASTEXITCODE -ne 0) { throw 'could not prepare the stata-package worktree' }

# Replace the branch content with the package.
Get-ChildItem $worktree -Force | Where-Object Name -ne '.git' | Remove-Item -Recurse -Force
# The .zip copy of the converter is for development only: net install would skip it.
Copy-Item (Join-Path $package '*') $worktree -Exclude '*.zip'
git -C $worktree add -A
git -C $worktree diff --cached --quiet
if ($LASTEXITCODE -eq 0) {
    Write-Host 'stata-package is already up to date.'
} else {
    git -C $worktree commit -q -m "build: Stata package from $source" -m "Built by scripts/publish-stata-package.ps1."
    if ($LASTEXITCODE -ne 0) { throw 'commit to stata-package failed' }
    Write-Host "Committed the Stata package from $source to stata-package. Push it with: git push origin stata-package"
}
git worktree remove --force $worktree | Out-Null
