param(
    [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"

function Assert-InWorkspace {
    param(
        [string]$Path,
        [string]$WorkspaceRoot
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $fullRoot = [System.IO.Path]::GetFullPath($WorkspaceRoot).TrimEnd("\") + "\"
    if (-not $fullPath.StartsWith($fullRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to modify a path outside the workspace: $fullPath"
    }
}

$workspaceRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$modelsRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "models\ACE-Step-1.5"))
$vendorRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "vendors\ACE-Step-1.5"))
$linkPath = [System.IO.Path]::GetFullPath((Join-Path $vendorRoot "checkpoints"))

Assert-InWorkspace -Path $modelsRoot -WorkspaceRoot $workspaceRoot
Assert-InWorkspace -Path $vendorRoot -WorkspaceRoot $workspaceRoot
Assert-InWorkspace -Path $linkPath -WorkspaceRoot $workspaceRoot

if (-not (Test-Path -LiteralPath $modelsRoot)) {
    if ($CheckOnly) {
        Write-Host "ACE-Step model directory is missing: $modelsRoot"
        exit 1
    }
    New-Item -ItemType Directory -Path $modelsRoot | Out-Null
}

if (-not (Test-Path -LiteralPath $vendorRoot)) {
    if ($CheckOnly) {
        Write-Host "ACE-Step vendor directory is missing: $vendorRoot"
        exit 1
    }
    New-Item -ItemType Directory -Path $vendorRoot | Out-Null
}

$existing = Get-Item -LiteralPath $linkPath -Force -ErrorAction SilentlyContinue
if ($null -ne $existing -and $existing.LinkType -eq "Junction") {
    $resolvedTarget = [System.IO.Path]::GetFullPath([string]$existing.Target)
    if ($resolvedTarget -ieq $modelsRoot) {
        Write-Host "ACE-Step checkpoints link is ready: $linkPath -> $modelsRoot"
        exit 0
    }
    throw "ACE-Step checkpoints points to an unexpected target: $resolvedTarget"
}

if ($CheckOnly) {
    Write-Host "ACE-Step checkpoints link is not configured."
    exit 1
}

if ($null -ne $existing) {
    $backupName = "checkpoints.downloaded-backup-" + (Get-Date -Format "yyyyMMdd-HHmmss")
    $backupPath = [System.IO.Path]::GetFullPath((Join-Path $vendorRoot $backupName))
    Assert-InWorkspace -Path $backupPath -WorkspaceRoot $workspaceRoot
    Write-Host "Preserving the previous vendor checkpoints directory:"
    Write-Host "  $linkPath"
    Write-Host "as:"
    Write-Host "  $backupPath"
    Move-Item -LiteralPath $linkPath -Destination $backupPath
}

New-Item -ItemType Junction -Path $linkPath -Target $modelsRoot | Out-Null
Write-Host "ACE-Step checkpoints link is ready: $linkPath -> $modelsRoot"
