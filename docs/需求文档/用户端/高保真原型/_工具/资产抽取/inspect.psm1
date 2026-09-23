# Inspect a cropped PNG: size + ASCII map + dominant colours.
Add-Type -AssemblyName System.Drawing
$outRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'frontend\uni-app\static\images'

function Inspect {
    param([string]$Rel, [int]$Cols = 46)
    $p = Join-Path $outRoot $Rel
    if (-not (Test-Path $p)) { "MISSING: $Rel"; return }
    $img = [System.Drawing.Image]::FromFile($p)
    $bmp = New-Object System.Drawing.Bitmap($img)
    "=== $Rel  ${($img.Width)}x$($img.Height)" -replace '\$\{\(', '' 
    "=== $Rel  $($img.Width)x$($img.Height) px   $([Math]::Round($img.Width/3,1))x$([Math]::Round($img.Height/3,1)) pt"
    $rows = [int][Math]::Max(1, [Math]::Round($Cols * $bmp.Height / $bmp.Width / 2.1))
    $px = $bmp.Width / $Cols; $py = $bmp.Height / $rows
    $hist = @{}
    for ($r = 0; $r -lt $rows; $r++) {
        $line = ''
        for ($cc = 0; $cc -lt $Cols; $cc++) {
            $sx = [int](($cc + 0.5) * $px); $sy = [int](($r + 0.5) * $py)
            if ($sx -ge $bmp.Width) { $sx = $bmp.Width - 1 }
            if ($sy -ge $bmp.Height) { $sy = $bmp.Height - 1 }
            $c = $bmp.GetPixel($sx, $sy)
            $mx = [Math]::Max($c.R, [Math]::Max($c.G, $c.B)); $mn = [Math]::Min($c.R, [Math]::Min($c.G, $c.B))
            $key = "$([int]($c.R/32))-$([int]($c.G/32))-$([int]($c.B/32))"
            if ($hist.ContainsKey($key)) { $hist[$key]++ } else { $hist[$key] = 1 }
            if ($c.B -gt 140 -and ($c.B - $c.R) -gt 50) { $line += 'B' }
            elseif ($mx -lt 90) { $line += '#' }
            elseif ($mx -lt 160) { $line += '-' }
            elseif ($mx -lt 230) { $line += '.' }
            elseif (($mx - $mn) -lt 14) { $line += ' ' }
            else { $line += 'o' }
        }
        "  $line|"
    }
    "  colour histogram (r/g/b bucket /32):"
    $hist.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 6 | ForEach-Object { "    $($_.Key) : $($_.Value)" }
    $img.Dispose(); $bmp.Dispose()
}
Export-ModuleMember -Function Inspect -Variable outRoot
