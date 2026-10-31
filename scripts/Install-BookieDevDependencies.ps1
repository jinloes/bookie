<#
.SYNOPSIS
  Installs Bookie's Windows development dependencies with winget.

.DESCRIPTION
  Installs each tool only if winget does not already list it, so the script is safe to re-run.
  Use -WhatIf to preview, and -Skip to leave out tools you manage another way.

  - Java 21 (Temurin JDK)       backend build and tests
  - Node.js LTS                 frontend and Tauri CLI
  - Rustup                      Tauri desktop shell
  - Visual Studio Build Tools   MSVC linker Rust needs on Windows (C++ workload)
  - Docker Desktop              SQL Server Testcontainers tests (./gradlew sqlServerTest)
  - 1Password CLI               scripts/Set-BookieLocalConfig.ps1
  - GitHub Copilot CLI          AI parsing via the logged-in Copilot user

.EXAMPLE
  .\scripts\Install-BookieDevDependencies.ps1 -WhatIf

.EXAMPLE
  .\scripts\Install-BookieDevDependencies.ps1 -Skip Docker,BuildTools
#>
[CmdletBinding(SupportsShouldProcess)]
param(
  [ValidateSet('Java', 'Node', 'Rust', 'BuildTools', 'Docker', 'OnePassword', 'Copilot')]
  [string[]]$Skip = @()
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
  throw 'winget not found. Install "App Installer" from the Microsoft Store, then re-run.'
}

$packages = @(
  @{ Key = 'Java'; Id = 'EclipseAdoptium.Temurin.21.JDK'; Name = 'Java 21 (Temurin)'; Command = 'java' }
  @{ Key = 'Node'; Id = 'OpenJS.NodeJS.LTS'; Name = 'Node.js LTS'; Command = 'node' }
  @{ Key = 'Rust'; Id = 'Rustlang.Rustup'; Name = 'Rustup'; Command = 'rustup' }
  @{
    Key = 'BuildTools'
    Id = 'Microsoft.VisualStudio.2022.BuildTools'
    Name = 'Visual Studio Build Tools (C++)'
    Override = '--wait --passive --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended'
  }
  @{ Key = 'Docker'; Id = 'Docker.DockerDesktop'; Name = 'Docker Desktop'; Command = 'docker' }
  @{ Key = 'OnePassword'; Id = 'AgileBits.1Password.CLI'; Name = '1Password CLI'; Command = 'op' }
  @{ Key = 'Copilot'; Id = 'GitHub.Copilot'; Name = 'GitHub Copilot CLI'; Command = 'copilot' }
)

# A tool counts as present if it is on PATH (e.g. Node from nvm) or winget lists the package.
function Test-Installed([hashtable]$Package) {
  if ($Package.Command -and (Get-Command $Package.Command -ErrorAction SilentlyContinue)) {
    return $true
  }
  # winget list exits non-zero when no installed package matches.
  & winget list --id $Package.Id --exact --accept-source-agreements *> $null
  return $LASTEXITCODE -eq 0
}

$installed = [System.Collections.Generic.List[string]]::new()
$failed = [System.Collections.Generic.List[string]]::new()

foreach ($package in $packages) {
  if ($Skip -contains $package.Key) {
    Write-Host "Skipping $($package.Name)"
    continue
  }
  if (Test-Installed $package) {
    Write-Host "$($package.Name) is already installed"
    continue
  }
  if (-not $PSCmdlet.ShouldProcess($package.Name, "winget install $($package.Id)")) {
    continue
  }
  $arguments = @(
    'install', '--id', $package.Id, '--exact', '--silent',
    '--accept-package-agreements', '--accept-source-agreements'
  )
  if ($package.Override) {
    $arguments += @('--override', $package.Override)
  }
  Write-Host "Installing $($package.Name)..."
  & winget @arguments
  if ($LASTEXITCODE -eq 0) {
    $installed.Add($package.Name)
  } else {
    Write-Warning "$($package.Name) failed to install (winget exit code $LASTEXITCODE)"
    $failed.Add($package.Name)
  }
}

if ($installed.Contains('Rustup')) {
  Write-Host 'Run "rustup default stable" in a new terminal to install the Rust toolchain.'
}
if ($installed.Contains('Docker Desktop')) {
  Write-Host 'Docker Desktop needs WSL 2: if it reports WSL is missing, run "wsl --install" as' `
    'administrator and reboot. Start Docker Desktop once before running ./gradlew sqlServerTest.'
}
if ($installed.Count -gt 0) {
  Write-Host 'Open a new terminal (or restart your IDE) so PATH changes take effect.'
}
if ($failed.Count -gt 0) {
  throw "Failed to install: $($failed -join ', ')"
}
