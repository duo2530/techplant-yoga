# Content report for small icon crops: how much of the frame is background vs coloured ink.
Add-Type -AssemblyName System.Drawing
$outRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'frontend\uni-app\static\images'

function Content {
    param([string]$Rel)
    $p = Join-Path $outRoot $Rel
    if (-not (Test-Path $p)) { return "MISSING $Rel" }
    $img = [System.Drawing.Image]::FromFile($p)
    $b = New-Object System.Drawing.Bitmap($img)
    $W = $b.Width; $H = $b.Height; $tot = $W * $H
    $white = 0; $blue = 0; $col = 0
    $bMinX=[int]::MaxValue;$bMinY=[int]::MaxValue;$bMaxX=-1;$bMaxY=-1
    $wMinX=[int]::MaxValue;$wMinY=[int]::MaxValue;$wMaxX=-1;$wMaxY=-1
    for ($y=0;$y -lt $H;$y++){ for($x=0;$x -lt $W;$x++){
        $c=$b.GetPixel($x,$y)
        $mx=[Math]::Max($c.R,[Math]::Max($c.G,$c.B)); $mn=[Math]::Min($c.R,[Math]::Min($c.G,$c.B))
        $sat = $mx-$mn
        if($mx -ge 245 -and $sat -lt 12){ $white++ }
        elseif($sat -ge 30 -or $mx -lt 200){ $col++
            if($x -lt $wMinX){$wMinX=$x};if($x -gt $wMaxX){$wMaxX=$x}
            if($y -lt $wMinY){$wMinY=$y};if($y -gt $wMaxY){$wMaxY=$y} }
        if($c.B -gt 140 -and ($c.B-$c.R) -gt 40){ $blue++
            if($x -lt $bMinX){$bMinX=$x};if($x -gt $bMaxX){$bMaxX=$x}
            if($y -lt $bMinY){$bMinY=$y};if($y -gt $bMaxY){$bMaxY=$y} }
    }}
    $cb = if($wMaxX -ge 0){"colour-ink px x$wMinX..$wMaxX y$wMinY..$wMaxY ($($wMaxX-$wMinX+1)x$($wMaxY-$wMinY+1))"}else{"none"}
    $bb = if($bMaxX -ge 0){"blue px x$bMinX..$bMaxX y$bMinY..$bMaxY ($($bMaxX-$bMinX+1)x$($bMaxY-$bMinY+1))"}else{"none"}
    $img.Dispose(); $b.Dispose()
    "{0,-30} {1,4}x{2,-4} white {3,5:N1}%  coloured {4,5:N1}%  blue {5,5:N1}%  | {6} | {7}" -f `
        $Rel,$W,$H,(100*$white/$tot),(100*$col/$tot),(100*$blue/$tot),$cb,$bb
}
Export-ModuleMember -Function Content -Variable outRoot
