param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$Port
)

$listeners = @(
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique
)

foreach ($processId in $listeners) {
    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
}

Start-Sleep -Milliseconds 600

if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    Write-Error "Port $Port is still listening after the local worker stop request."
    exit 1
}

Write-Output "Stopped local listener on port $Port."
