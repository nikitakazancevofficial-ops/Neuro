param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$Port,

    [ValidateRange(1, 120)]
    [int]$TimeoutSeconds = 30
)

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
        Write-Output "Port $Port is ready."
        exit 0
    }
    Start-Sleep -Milliseconds 500
} while ((Get-Date) -lt $deadline)

Write-Error "Port $Port did not become ready within $TimeoutSeconds seconds."
exit 1
