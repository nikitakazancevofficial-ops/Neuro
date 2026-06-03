param(
    [switch]$ConfirmPhysicalRepair
)

$ErrorActionPreference = "Stop"

if (-not $ConfirmPhysicalRepair) {
    Write-Host "Storage repair was not acknowledged."
    Write-Host "Run this script with -ConfirmPhysicalRepair only after the hardware and cables were checked."
    exit 2
}

try {
    $volume = Get-Volume -DriveLetter C
    $freeGb = [math]::Round($volume.SizeRemaining / 1GB, 1)
    Write-Host "System drive C: $freeGb GB free; health=$($volume.HealthStatus); status=$($volume.OperationalStatus)"

    if ($freeGb -lt 20) {
        throw "C: has less than 20 GB free."
    }

    if ($volume.HealthStatus -ne "Healthy" -or $volume.OperationalStatus -ne "OK") {
        throw "Windows reports a C: filesystem problem."
    }

    $bootTime = (Get-CimInstance Win32_OperatingSystem).LastBootUpTime
    $storageEvents = @(
        Get-WinEvent -FilterHashtable @{
            LogName = "System"
            StartTime = $bootTime
        } -ErrorAction SilentlyContinue |
            Where-Object {
                ($_.ProviderName -eq "disk" -and $_.Id -in @(7, 11, 15, 51, 55, 129, 153)) -or
                ($_.ProviderName -eq "Ntfs" -and $_.Id -in @(55, 130, 132)) -or
                ($_.ProviderName -match "storahci|stornvme" -and $_.Id -eq 129)
            }
    )
    $inPageEvents = @(
        Get-WinEvent -FilterHashtable @{
            LogName = "Application"
            ProviderName = "Windows Error Reporting"
            Id = 1001
            StartTime = $bootTime
        } -ErrorAction SilentlyContinue |
            Where-Object { $_.Message -match "InPageError" }
    )

    if ($storageEvents.Count -gt 0 -or $inPageEvents.Count -gt 0) {
        throw "Windows logged fresh storage errors after the current boot. Heavy AI workers remain disabled."
    }

    $storageDirectory = Join-Path $PSScriptRoot ".local_storage"
    $markerPath = Join-Path $storageDirectory "storage-repair-ack.txt"
    New-Item -ItemType Directory -Path $storageDirectory -Force | Out-Null
    [IO.File]::WriteAllText(
        $markerPath,
        (Get-Date).ToUniversalTime().ToString("o"),
        [Text.UTF8Encoding]::new($false)
    )

    Write-Host "Storage repair acknowledged."
    Write-Host "Heavy AI workers will now be allowed only while Windows reports no new storage errors."
} catch {
    Write-Host "WARNING: storage repair acknowledgement failed: $($_.Exception.Message)"
    exit 2
}

exit 0
