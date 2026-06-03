$candidates = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object {
        $_.AddressState -eq "Preferred" -and
        $_.IPAddress -notlike "127.*" -and
        $_.IPAddress -notlike "169.254.*" -and
        $_.InterfaceAlias -notmatch "VirtualBox|VMware|WSL|Loopback|vEthernet"
    } |
    Sort-Object @{
        Expression = {
            if ($_.PrefixOrigin -eq "Dhcp") { 0 } else { 1 }
        }
    }, InterfaceIndex

$preferred = $candidates | Select-Object -First 1 -ExpandProperty IPAddress
if (-not $preferred) {
    $preferred = "127.0.0.1"
}

Write-Output $preferred
