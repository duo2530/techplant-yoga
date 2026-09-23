# Ad-hoc A/B comparison harness: compare a hypothetical crop (nudge) vs another.
# Prints gray-scale ASCII maps comparing two regions so a bad offset is visible without images.
Add-Type -AssemblyName System.Drawing
$srcDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'docs\需求文档\用户端\用户端小程序截图'

function Get-Bmp([string]$Name) {
    $img = [System.Drawing.Image]::FromFile((Join-Path $script:srcDir $Name))
    $bmp = New-Object System.Drawing.Bitmap($img); $img.Dispose(); return $bmp
}

# ASCII map of a logical rect (downsampled). Chars: ' '=white, '.'=light, '-'=mid, '#'=dark, 'B'=blue
function Show-Map {
    param([System.Drawing.Bitmap]$Bmp,[double]$X,[double]$Y,[double]$W,[double]$H,
          [int]$Cols = 60, [int]$Rows = 0, [string]$Title = '')
    $px=[int][Math]::Round($X*3); $py=[int][Math]::Round($Y*3)
    $pw=[int][Math]::Round($W*3); $ph=[int][Math]::Round($H*3)
    if($Rows -le 0){ $Rows = [int][Math]::Max(1,[Math]::Round($Cols * $ph / $pw / 2.1)) }
    "=== $Title  src($px,$py,$pw,$ph) cols=$Cols rows=$Rows"
    for($r=0;$r -lt $Rows;$r++){
        $line=''
        for($cc=0;$cc -lt $Cols;$cc++){
            $sx=$px+[int](($cc+0.5)*$pw/$Cols); $sy=$py+[int](($r+0.5)*$ph/$Rows)
            if($sx -ge $Bmp.Width){$sx=$Bmp.Width-1}; if($sy -ge $Bmp.Height){$sy=$Bmp.Height-1}
            $c=$Bmp.GetPixel($sx,$sy)
            $mx=[Math]::Max($c.R,[Math]::Max($c.G,$c.B))
            if($c.B -gt 140 -and ($c.B-$c.R) -gt 60){ $line+='B' }
            elseif($mx -lt 90){$line+='#'} elseif($mx -lt 160){$line+='-'}
            elseif($mx -lt 232){$line+='.'} else {$line+=' '}
        }
        "$($line)|"
    }
}


function Map-To-File {
    param([System.Drawing.Bitmap]$Bmp,[double]$X,[double]$Y,[double]$W,[double]$H,
          [int]$Cols,[int]$Rows,[string]$Path,[string]$Title='')
    $px=[int][Math]::Round($X*3); $py=[int][Math]::Round($Y*3)
    $pw=[int][Math]::Round($W*3); $ph=[int][Math]::Round($H*3)
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("=== $Title  logical($X,$Y,$W,$H) src($px,$py,$pw,$ph) cols=$Cols rows=$Rows")
    for($r=0;$r -lt $Rows;$r++){
        $line=''
        for($cc=0;$cc -lt $Cols;$cc++){
            $sx=$px+[int](($cc+0.5)*$pw/$Cols); $sy=$py+[int](($r+0.5)*$ph/$Rows)
            if($sx -lt 0){$sx=0}; if($sy -lt 0){$sy=0}
            if($sx -ge $Bmp.Width){$sx=$Bmp.Width-1}; if($sy -ge $Bmp.Height){$sy=$Bmp.Height-1}
            $c=$Bmp.GetPixel($sx,$sy)
            $mx=[Math]::Max($c.R,[Math]::Max($c.G,$c.B))
            if($c.B -gt 140 -and ($c.B-$c.R) -gt 60){ $line+='B' }
            elseif($mx -lt 90){$line+='#'} elseif($mx -lt 160){$line+='-'}
            elseif($mx -lt 232){$line+='.'} else {$line+=' '}
        }
        $ly=[Math]::Round(($py + ($r+0.5)*$ph/$Rows)/3,1)
        [void]$sb.AppendLine(("{0,7}|{1}|" -f $ly,$line))
    }
    Set-Content -Path $Path -Value $sb.ToString() -Encoding UTF8
    "wrote $Path"
}
Export-ModuleMember -Function Get-Bmp, Show-Map, Map-To-File -Variable srcDir
