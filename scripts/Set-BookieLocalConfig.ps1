<#
.SYNOPSIS
  Configures Bookie on a Windows machine from the "Bookie" 1Password vault.

.DESCRIPTION
  - Writes the Outlook app and Azure SQL connection settings to .env.local.properties
    (other keys already in the file are kept) and restricts the file to the current user.
  - Sets BOOKIE_DB_VENDOR as a user environment variable; it must be a real environment
    variable because it selects the Spring profile before .env.local.properties is read.
  - Stores the Azure SQL password and the backup key in Windows Credential Manager, where
    the backend reads them. Secrets are never printed.

  Requires the 1Password CLI (op) signed in, e.g. via the 1Password desktop app integration.

.EXAMPLE
  .\scripts\Set-BookieLocalConfig.ps1
#>
[CmdletBinding()]
param(
  [string]$Vault = 'Bookie',
  [string]$OutputPath = (Join-Path (Split-Path $PSScriptRoot -Parent) '.env.local.properties'),
  [switch]$SkipCredentialManager
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command op -ErrorAction SilentlyContinue)) {
  throw '1Password CLI (op) not found. Install it with: winget install AgileBits.1Password.CLI'
}

function Read-OpField([string]$Item, [string]$Field) {
  $value = & op read --no-newline "op://$Vault/$Item/$Field"
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrEmpty($value)) {
    throw "Could not read op://$Vault/$Item/$Field from 1Password."
  }
  return $value
}

# Spring parses this file as Java properties, so backslashes must be escaped.
function ConvertTo-PropertyValue([string]$Value) {
  return $Value.Replace('\', '\\')
}

$vendor = Read-OpField 'Bookie Azure SQL' 'vendor'
$dbUser = Read-OpField 'Bookie Azure SQL' 'username'
$settings = [ordered]@{
  BOOKIE_DB_URL         = Read-OpField 'Bookie Azure SQL' 'url'
  BOOKIE_DB_USERNAME    = $dbUser
  OUTLOOK_CLIENT_ID     = Read-OpField 'Bookie Outlook App' 'client_id'
  OUTLOOK_TENANT_ID     = Read-OpField 'Bookie Outlook App' 'tenant_id'
  OUTLOOK_CLIENT_SECRET = Read-OpField 'Bookie Outlook App' 'client_secret'
}

$lines = [System.Collections.Generic.List[string]]::new()
if (Test-Path $OutputPath) {
  foreach ($line in Get-Content $OutputPath) {
    $key = ($line -split '=', 2)[0].Trim()
    if (-not $settings.Contains($key)) {
      $lines.Add($line)
    }
  }
}
foreach ($entry in $settings.GetEnumerator()) {
  $lines.Add("$($entry.Key)=$(ConvertTo-PropertyValue $entry.Value)")
}

if (-not (Test-Path $OutputPath)) {
  New-Item -ItemType File -Path $OutputPath | Out-Null
}
# Restrict access before writing the client secret into the file.
& icacls $OutputPath /inheritance:r /grant:r "$($env:USERNAME):(F)" | Out-Null
[System.IO.File]::WriteAllLines($OutputPath, $lines)
Write-Host "Wrote $($settings.Count) settings to $OutputPath"

[Environment]::SetEnvironmentVariable('BOOKIE_DB_VENDOR', $vendor, 'User')
$env:BOOKIE_DB_VENDOR = $vendor
Write-Host "Set user environment variable BOOKIE_DB_VENDOR=$vendor"

if ($SkipCredentialManager) {
  return
}

Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;

public static class BookieCredentialStore {
  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
  private struct CREDENTIAL {
    public int Flags;
    public int Type;
    public string TargetName;
    public string Comment;
    public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
    public int CredentialBlobSize;
    public IntPtr CredentialBlob;
    public int Persist;
    public int AttributeCount;
    public IntPtr Attributes;
    public string TargetAlias;
    public string UserName;
  }

  [DllImport("Advapi32.dll", EntryPoint = "CredWriteW", CharSet = CharSet.Unicode, SetLastError = true)]
  private static extern bool CredWrite(ref CREDENTIAL credential, int flags);

  // Same layout the backend reads: generic credential, target "<service>:<account>",
  // UTF-16LE secret, persisted for this user on this machine.
  public static void Write(string service, string account, string secret) {
    byte[] blob = System.Text.Encoding.Unicode.GetBytes(secret);
    IntPtr buffer = Marshal.AllocHGlobal(blob.Length);
    try {
      Marshal.Copy(blob, 0, buffer, blob.Length);
      CREDENTIAL credential = new CREDENTIAL {
        Type = 1,
        TargetName = service + ":" + account,
        UserName = account,
        CredentialBlobSize = blob.Length,
        CredentialBlob = buffer,
        Persist = 2
      };
      if (!CredWrite(ref credential, 0)) {
        throw new Win32Exception(Marshal.GetLastWin32Error());
      }
    } finally {
      for (int i = 0; i < blob.Length; i++) {
        Marshal.WriteByte(buffer, i, 0);
      }
      Marshal.FreeHGlobal(buffer);
      Array.Clear(blob, 0, blob.Length);
    }
  }
}
'@

[BookieCredentialStore]::Write('com.bookie.sqlserver', $dbUser, (Read-OpField 'Bookie Azure SQL' 'password'))
Write-Host "Stored the Azure SQL password in Credential Manager (com.bookie.sqlserver:$dbUser)"

[BookieCredentialStore]::Write('com.bookie.backup-key', 'default', (Read-OpField 'Bookie Backup Key' 'password'))
Write-Host 'Stored the backup key in Credential Manager (com.bookie.backup-key:default)'

Write-Host 'Done. Restart your terminal or IDE so it picks up BOOKIE_DB_VENDOR.'
