# Content sanity: mean colour, stddev, unique-colour count, edge uniformity.
# Flags solid blocks (failed/blank crops) and flat edges (UI bleed).
Add-Type -AssemblyName System.Drawing
$outRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'frontend\uni-app\static\images'

function Stats {
    param([string]$Rel, [int]$Step = 2)
    $p = Join-Path $outRoot $Rel
    if (-not (Test-Path $p)) { return "MISSING $Rel" }
    $img = [System.Drawing.Image]::FromFile($p); $b = New-Object System.Drawing.Bitmap($img)
    $W = $b.Width; $H = $b.Height
    $cols = New-Object 'System.Collections.Generic.HashSet[int]'
    $sum = 0.0; $sum2 = 0.0; $n = 0; $sr = 0.0; $sg = 0.0; $sb = 0.0
    for ($y = 0; $y -lt $H; $y += $Step) {
        for ($x = 0; $x -lt $W; $x += $Step) {
            $c = $b.GetPixel($x, $y)
            $lum = 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B
            $sum += $lum; $sum2 += $lum * $lum; $n++
            $sr += $c.R; $sg += $c.G; $sb += $c.B
            [void]$cols.Add((($c.R -shr 3) -shl 10) -bor (($c.G -shr 3) -shl 5) -bor ($c.B -shr 3))
        }
    }
    $mean = $sum / $n; $var = ($sum2 / $n) - ($mean * $mean); if ($var -lt 0) { $var = 0 }
    $sd = [Math]::Sqrt($var)
    # border means (1-pixel frame)
    $bt = 0.0; $bb = 0.0; $bl = 0.0; $br = 0.0
    for ($x = 0; $x -lt $W; $x++) { $bt += $b.GetPixel($x,0).R + $b.GetPixel($x,0).G + $b.GetPixel($x,0).B
                                    $bb += $b.GetPixel($x,$H-1).R + $b.GetPixel($x,$H-1).G + $b.GetPixel($x,$H-1).B }
    for ($y = 0; $y -lt $H; $y++) { $bl += $b.GetPixel(0,$y).R + $b.GetPixel(0,$y).G + $b.GetPixel(0,$y).B
                                    $br += $b.GetPixel($W-1,$y).R + $b.GetPixel($W-1,$y).G + $b.GetPixel($W-1,$y).B }
    $bt=[int]($bt/(3*$W)); $bb=[int]($bb/(3*$W)); $bl=[int]($bl/(3*$H)); $br=[int]($br/(3*$H))
    $img.Dispose(); $b.Dispose()
    "{0,-30} {1,4}x{2,-4} meanRGB=({3,3},{4,3},{5,3}) sd={6,6:N1} uniqCols={7,5}  border T{8} B{9} L{10} R{11}" -f `
        $Rel,$W,$H,[int]($sr/$n),[int]($sg/$n),[int]($sb/$n),$sd,$cols.Count,$bt,$bb,$bl,$br
}
Export-ModuleMember -Function Stats -Variable outRoot
