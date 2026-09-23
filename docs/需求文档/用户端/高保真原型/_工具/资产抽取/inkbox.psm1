# Measure ink bbox + edge margins of cropped assets, in logical pt (px/3).
Add-Type -AssemblyName System.Drawing
$outRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'frontend\uni-app\static\images'

function InkBox {
    param([string]$Rel, [int]$MinInk = 235, [int]$Step = 1)
    $p = Join-Path $outRoot $Rel
    if (-not (Test-Path $p)) { return "MISSING $Rel" }
    $img = [System.Drawing.Image]::FromFile($p)
    $bmp = New-Object System.Drawing.Bitmap($img)
    $W = $bmp.Width; $H = $bmp.Height
    $minX = [int]::MaxValue; $minY = [int]::MaxValue; $maxX = -1; $maxY = -1; $n = 0
    for ($y = 0; $y -lt $H; $y += $Step) {
        for ($x = 0; $x -lt $W; $x += $Step) {
            $c = $bmp.GetPixel($x, $y)
            if ([Math]::Max($c.R, [Math]::Max($c.G, $c.B)) -lt $MinInk) {
                $n++
                if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
                if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y }
            }
        }
    }
    $img.Dispose(); $bmp.Dispose()
    if ($n -eq 0) { return ("{0,-34} {1,4}x{2,-4} NO INK" -f $Rel, $W, $H) }
    $L = $minX; $T = $minY; $R = $W - 1 - $maxX; $B = $H - 1 - $maxY
    $cx = ($minX + $maxX) / 2; $cy = ($minY + $maxY) / 2
    $sx = [Math]::Round(($cx - ($W - 1) / 2) / 3, 2); $sy = [Math]::Round(($cy - ($H - 1) / 2) / 3, 2)
    return ("{0,-34} {1,4}x{2,-4} ink {3}x{4}px  margins L{5} T{6} R{7} B{8}px  centreOff({9},{10})pt" -f `
        $Rel, $W, $H, ($maxX - $minX + 1), ($maxY - $minY + 1), $L, $T, $R, $B, $sx, $sy)
}
Export-ModuleMember -Function InkBox -Variable outRoot
