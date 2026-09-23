# Compact run-length scans: print colour boundaries along a line.
Add-Type -AssemblyName System.Drawing
$srcDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'docs\需求文档\用户端\用户端小程序截图'
function Get-Bmp([string]$Name) {
    $img = [System.Drawing.Image]::FromFile((Join-Path $srcDir $Name))
    $bmp = New-Object System.Drawing.Bitmap($img); $img.Dispose(); return $bmp
}
function Color-Class { param($c)
    $mx=[Math]::Max($c.R,[Math]::Max($c.G,$c.B)); $mn=[Math]::Min($c.R,[Math]::Min($c.G,$c.B))
    if($c.B -gt 140 -and ($c.B-$c.R) -gt 60){ return 'BLUE' }
    if($c.G -gt 150 -and $c.B -gt 150 -and $c.R -lt 200 -and ($c.B-$c.R) -gt 8 -and ($c.G-$c.R) -gt 0){ return 'CYAN' }
    if($mx -ge 245 -and ($mx-$mn) -lt 12){ return 'WHITE' }
    if($mx -lt 120){ return 'DARK' }
    if($mx -lt 210){ return 'MID' }
    return 'LIGHT'
}
function Scan-Y { param($Bmp,[int]$X,[int]$Y0,[int]$Y1,[int]$Step=3)
    "--- vertical x=$X (logical $([Math]::Round($X/3,2))) y $Y0..$Y1"
    $prev=$null; $start=$Y0
    for($y=$Y0;$y -le $Y1;$y+=$Step){
        $cl=Color-Class ($Bmp.GetPixel($X,$y))
        if($cl -ne $prev){
            if($prev -ne $null){ "  logical $([Math]::Round($start/3,1))..$([Math]::Round(($y-$Step)/3,1)) : ${prev}" }
            $prev=$cl; $start=$y
        }
    }
    "  logical $([Math]::Round($start/3,1))..$([Math]::Round($Y1/3,1)) : ${prev}"
}
function Scan-X { param($Bmp,[int]$Y,[int]$X0,[int]$X1,[int]$Step=3)
    "--- horizontal y=$Y (logical $([Math]::Round($Y/3,2))) x $X0..$X1"
    $prev=$null; $start=$X0
    for($x=$X0;$x -le $X1;$x+=$Step){
        $cl=Color-Class ($Bmp.GetPixel($x,$Y))
        if($cl -ne $prev){
            if($prev -ne $null){ "  logical $([Math]::Round($start/3,1))..$([Math]::Round(($x-$Step)/3,1)) : ${prev}" }
            $prev=$cl; $start=$x
        }
    }
    "  logical $([Math]::Round($start/3,1))..$([Math]::Round($X1/3,1)) : ${prev}"
}

# Bounding box of a colour class inside a logical window
function BB { param($Bmp,[string]$Want,[double]$L,[double]$T,[double]$R,[double]$B,[int]$Step=2,[string]$Tag='')
    $x0=[int][Math]::Max(0,[Math]::Floor($L*3)); $y0=[int][Math]::Max(0,[Math]::Floor($T*3))
    $x1=[int][Math]::Min($Bmp.Width-1,[Math]::Ceiling($R*3)); $y1=[int][Math]::Min($Bmp.Height-1,[Math]::Ceiling($B*3))
    $minX=[int]::MaxValue;$minY=[int]::MaxValue;$maxX=-1;$maxY=-1;$n=0
    for($y=$y0;$y -le $y1;$y+=$Step){ for($x=$x0;$x -le $x1;$x+=$Step){
        if((Color-Class ($Bmp.GetPixel($x,$y))) -eq $Want){ $n++
            if($x -lt $minX){$minX=$x}; if($x -gt $maxX){$maxX=$x}
            if($y -lt $minY){$minY=$y}; if($y -gt $maxY){$maxY=$y} } } }
    if($n -eq 0){ return "[$Tag] ${Want}: none in ($L,$T)-($R,$B)" }
    "[$Tag] ${Want} n=$n  logical x=$([Math]::Round($minX/3,2))..$([Math]::Round(($maxX+$Step)/3,2))  y=$([Math]::Round($minY/3,2))..$([Math]::Round(($maxY+$Step)/3,2))"
}
Export-ModuleMember -Function Get-Bmp,Scan-Y,Scan-X,BB,Color-Class -Variable srcDir
