# Measure bounding boxes of assets inside design screenshots.
# All output in LOGICAL pt (source px / 3).
Add-Type -AssemblyName System.Drawing

$srcDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'docs\需求文档\用户端\用户端小程序截图'

function Get-Bmp([string]$Name) {
    $img = [System.Drawing.Image]::FromFile((Join-Path $srcDir $Name))
    $bmp = New-Object System.Drawing.Bitmap($img)
    $img.Dispose()
    return $bmp
}

# Returns logical bbox of pixels matching $Pred inside logical search window.
# Also returns the window-edge contact flags to detect truncation.
function Measure-Region {
    param(
        [System.Drawing.Bitmap]$Bmp,
        [scriptblock]$Pred,
        [double]$L, [double]$T, [double]$R, [double]$B,
        [int]$Step = 3,
        [string]$Label = ''
    )
    $x0 = [int][Math]::Floor($L * 3); $x1 = [int][Math]::Ceiling($R * 3)
    $y0 = [int][Math]::Floor($T * 3); $y1 = [int][Math]::Ceiling($B * 3)
    $x0 = [Math]::Max(0, $x0); $y0 = [Math]::Max(0, $y0)
    $x1 = [Math]::Min($Bmp.Width - 1, $x1); $y1 = [Math]::Min($Bmp.Height - 1, $y1)
    $minX = [int]::MaxValue; $minY = [int]::MaxValue; $maxX = -1; $maxY = -1; $n = 0
    for ($y = $y0; $y -le $y1; $y += $Step) {
        for ($x = $x0; $x -le $x1; $x += $Step) {
            $c = $Bmp.GetPixel($x, $y)
            if (& $Pred $c) {
                $n++
                if ($x -lt $minX) { $minX = $x }
                if ($x -gt $maxX) { $maxX = $x }
                if ($y -lt $minY) { $minY = $y }
                if ($y -gt $maxY) { $maxY = $y }
            }
        }
    }
    if ($n -eq 0) { return "[$Label] NO MATCH in window ($L,$T)-($R,$B)" }
    $lx = [Math]::Round($minX / 3, 2); $ly = [Math]::Round($minY / 3, 2)
    $lw = [Math]::Round(($maxX - $minX + $Step) / 3, 2); $lh = [Math]::Round(($maxY - $minY + $Step) / 3, 2)
    $touch = @()
    if ($minX -le $x0 + $Step) { $touch += 'LEFT' }
    if ($maxX -ge $x1 - $Step) { $touch += 'RIGHT' }
    if ($minY -le $y0 + $Step) { $touch += 'TOP' }
    if ($maxY -ge $y1 - $Step) { $touch += 'BOTTOM' }
    $tw = if ($touch.Count) { ' touches:' + ($touch -join ',') } else { '' }
    return "[$Label] n=$n bbox logical x=$lx y=$ly w=$lw h=$lh  (x2=$([Math]::Round(($maxX+$Step)/3,2)) y2=$([Math]::Round(($maxY+$Step)/3,2)))$tw"
}

# predicates
$IsBlue = { param($c) $max = [Math]::Max($c.R, [Math]::Max($c.G, $c.B)); $min = [Math]::Min($c.R, [Math]::Min($c.G, $c.B)); ($c.B -gt 140) -and (($c.B - $c.R) -gt 60) }
$IsInk  = { param($c) ([Math]::Max($c.R, [Math]::Max($c.G, $c.B)) -lt 230) }
$IsDark = { param($c) ([Math]::Max($c.R, [Math]::Max($c.G, $c.B)) -lt 190) }

function Avg-Row { param([System.Drawing.Bitmap]$B,[int]$Y,[int]$X0,[int]$X1)
    $r=0;$g=0;$b=0;$n=0
    for($x=$X0;$x -le $X1;$x++){ $c=$B.GetPixel($x,$Y); $r+=$c.R;$g+=$c.G;$b+=$c.B;$n++ }
    "$([int]($r/$n)),$([int]($g/$n)),$([int]($b/$n))"
}

Export-ModuleMember -Function Get-Bmp, Measure-Region, Avg-Row -Variable IsBlue, IsInk, IsDark, srcDir
