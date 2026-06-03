param(
    [int]$MinimumFreeGb = 20,
    [int]$LookbackHours = 168
)

$ErrorActionPreference = "Stop"

try {
    $volume = Get-Volume -DriveLetter C
    $freeGb = [math]::Round($volume.SizeRemaining / 1GB, 1)
    Write-Host "System drive C: $freeGb GB free; health=$($volume.HealthStatus); status=$($volume.OperationalStatus)"

    if ($freeGb -lt $MinimumFreeGb) {
        Write-Host "WARNING: heavy AI workers are disabled because C: has less than $MinimumFreeGb GB free."
        exit 2
    }

    if ($volume.HealthStatus -ne "Healthy" -or $volume.OperationalStatus -ne "OK") {
        Write-Host "WARNING: heavy AI workers are disabled because Windows reports a C: filesystem problem."
        exit 2
    }

    $disk = Get-Partition -DriveLetter C | Get-Disk
    $diskNumber = $disk.Number
    $since = (Get-Date).AddHours(-$LookbackHours)
    $repairMarkerPath = Join-Path $PSScriptRoot ".local_storage\storage-repair-ack.txt"
    if (Test-Path -LiteralPath $repairMarkerPath) {
        $repairMarkerText = (Get-Content -LiteralPath $repairMarkerPath -Raw).Trim()
        $repairMarkerUtc = [datetime]::MinValue
        if ([datetime]::TryParse(
            $repairMarkerText,
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::RoundtripKind,
            [ref]$repairMarkerUtc
        ) -and $repairMarkerUtc -gt $since) {
            $since = $repairMarkerUtc.ToLocalTime()
            Write-Host "Monitoring storage events since the confirmed repair: $($since.ToString('yyyy-MM-dd HH:mm:ss'))"
        }
    }
    $bsodEvents = @(
        Get-WinEvent -FilterHashtable @{
            LogName = "System"
            ProviderName = "Microsoft-Windows-WER-SystemErrorReporting"
            Id = 1001
            StartTime = $since
        } -ErrorAction SilentlyContinue |
            Where-Object { $_.Message -match "0x000000ef" }
    )
    $inPageEvents = @(
        Get-WinEvent -FilterHashtable @{
            LogName = "Application"
            ProviderName = "Windows Error Reporting"
            Id = 1001
            StartTime = $since
        } -ErrorAction SilentlyContinue |
            Where-Object { $_.Message -match "InPageError" }
    )
    $diskEvents = @(
        Get-WinEvent -FilterHashtable @{
            LogName = "System"
            ProviderName = "disk"
            Id = @(51, 153)
            StartTime = $since
        } -ErrorAction SilentlyContinue |
            Where-Object {
                $_.Message -match "Harddisk$diskNumber\b" -or
                $_.Message -match "диск[ае]?\s+$diskNumber\b" -or
                $_.Message -match "disk\s+$diskNumber\b"
            }
    )

    if ($bsodEvents.Count -gt 0 -or $inPageEvents.Count -gt 0) {
        $latestCriticalEvent = @($bsodEvents + $inPageEvents) |
            Sort-Object TimeCreated -Descending |
            Select-Object -First 1
        Write-Host "WARNING: heavy AI workers are disabled because Windows logged $($bsodEvents.Count) recent CRITICAL_PROCESS_DIED crashes and $($inPageEvents.Count) InPageError reports."
        Write-Host "Latest critical storage-related event: $($latestCriticalEvent.TimeCreated.ToString('yyyy-MM-dd HH:mm:ss'))"
        Write-Host "Back up important files and check or replace the system SSD before running FLUX or ACE-Step."
        exit 2
    }

    if ($diskEvents.Count -gt 0) {
        $lastEvent = $diskEvents | Sort-Object TimeCreated -Descending | Select-Object -First 1
        Write-Host "WARNING: heavy AI workers are disabled because Windows logged $($diskEvents.Count) recent I/O retries for system disk $diskNumber."
        Write-Host "Latest storage event: $($lastEvent.TimeCreated.ToString('yyyy-MM-dd HH:mm:ss'))"
        Write-Host "Back up important files and check or replace the system SSD before running FLUX or ACE-Step."
        exit 2
    }
} catch {
    Write-Host "WARNING: system drive preflight could not complete: $($_.Exception.Message)"
    Write-Host "Heavy AI workers are disabled until the storage check succeeds."
    exit 2
}

Write-Host "System drive preflight passed."
exit 0
