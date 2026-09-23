# Rasterize the two prototype empty-state SVGs to PNG via headless Chrome.
# - background: transparent (alpha preserved)
# - rendered at 3x, then trimmed on the ink bbox so the final image is exactly
#   the SVG's logical box at 3x (with a small margin so outer strokes are not clipped).
# Chrome invocation follows docs/原型/_工具/渲染对比.ps1 (that script is left untouched).
Add-Type -AssemblyName System.Drawing

# 仓库根：从脚本位置向上找到含 frontend\uni-app 的目录
# （本脚本位于 docs\原型\_工具\资产抽取\，位置变化也能正确定位）
$root = $PSScriptRoot
while ($root -and -not (Test-Path (Join-Path $root 'frontend\uni-app'))) { $root = Split-Path -Parent $root }
$tmpDir  = Join-Path $root 'docs\原型\_工具\渲染'
$outRoot = Join-Path $root 'frontend\uni-app\static\images'
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

$Chrome = "C:\Program Files\Google\Chrome\Application\chrome.exe"

function Render-SvgPage {
    param([string]$HtmlPath,[string]$PngPath,[int]$WinW,[int]$WinH)
    if (Test-Path $PngPath) { Remove-Item $PngPath -Force }
    $Prof = Join-Path $env:TEMP ("dsh-chrome-" + [guid]::NewGuid().ToString('N'))
    $u = ([System.Uri]$HtmlPath).AbsoluteUri
    $err = & $Chrome --headless=new --disable-gpu --hide-scrollbars --no-first-run `
        --user-data-dir="$Prof" --virtual-time-budget=5000 `
        --default-background-color=00000000 --force-device-scale-factor=3 `
        --window-size="$WinW,$WinH" --screenshot="$PngPath" $u 2>&1 | Out-String
    if (-not (Test-Path $PngPath)) {
        "FAIL render: $HtmlPath"
        ($err -split "`r?`n" | Where-Object { $_.Trim() -ne '' } | Select-Object -First 4) | ForEach-Object { "    $_" }
        return $null
    }
    return $PngPath
}

# Find the crop rect (in render px) whose placement makes the SVG box sit with
# symmetric outer margin.  Strategy: the rendered page draws the SVG at a known
# CSS offset (PAD).  At 3x that is PAD*3.  We verify by scanning ink and then
# emit a box of exactly boxW*3 x boxH*3 centred on the SVG box.
function Trim-ToBox {
    param([System.Drawing.Bitmap]$Bmp,[int]$PadPx,[int]$BoxW,[int]$BoxH,[string]$OutPath)
    # ink bbox
    $minX=[int]::MaxValue;$minY=[int]::MaxValue;$maxX=-1;$maxY=-1
    for($y=0;$y -lt $Bmp.Height;$y++){ for($x=0;$x -lt $Bmp.Width;$x++){
        if($Bmp.GetPixel($x,$y).A -gt 8){ if($x -lt $minX){$minX=$x}; if($x -gt $maxX){$maxX=$x}
                                          if($y -lt $minY){$minY=$y}; if($y -gt $maxY){$maxY=$y} } } }
    if($maxX -lt 0){ return "NO INK" }
    $svgX = $PadPx; $svgY = $PadPx
    $rect = New-Object System.Drawing.Rectangle($svgX,$svgY,$BoxW,$BoxH)
    $bmp2 = New-Object System.Drawing.Bitmap($BoxW,$BoxH,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $bmp2.SetResolution(72,72)
    $g = [System.Drawing.Graphics]::FromImage($bmp2)
    $g.DrawImage($Bmp,(New-Object System.Drawing.Rectangle(0,0,$BoxW,$BoxH)),$rect,[System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $bmp2.Save($OutPath,[System.Drawing.Imaging.ImageFormat]::Png)
    $bmp2.Dispose()
    return ("ink render-bbox x{0}..{1} y{2}..{3}  svgbox in render x{4}..{5} y{6}..{7}" -f `
        $minX,$maxX,$minY,$maxY,$svgX,($svgX+$BoxW-1),$svgY,($svgY+$BoxH-1))
}

# ---- activity icon: 39x36pt SVG whose 2.25pt strokes extend ~1.13pt outside the viewBox.
#      Render inside a padded box so the stroke is fully painted, then crop to the
#      padded logical box (39 + 2*2 = 43pt x 36 + 2*2 = 40pt) -> 129x120 px at 3x.
$pad = 0   # no extra margin: the SVG box is the intended canvas
$jobs = @(
  @{ html='_empty-activity.html'; png='_empty-activity.png'; out='empty\activity.png'
     boxW=[int](39*3); boxH=[int](36*3); paddedW=[int]((39+2*$pad)*3); paddedH=[int]((36+2*$pad)*3)
     padPx=$pad*3; winW=120; winH=100 },
  @{ html='_empty-message.html';  png='_empty-message.png';  out='empty\message.png'
     boxW=[int](66*3); boxH=[int](52*3); paddedW=[int]((66+2*$pad)*3); paddedH=[int]((52+2*$pad)*3)
     padPx=$pad*3; winW=120; winH=100 }
)

foreach ($j in $jobs) {
    $htmlPath = Join-Path $tmpDir $j.html
    $pngPath  = Join-Path $tmpDir $j.png
    # rewrite the page so the svg sits inside a padded transparent box
    $svgFile = Get-Content $htmlPath -Raw
    if (Render-SvgPage -HtmlPath $htmlPath -PngPath $pngPath -WinW $j.winW -WinH $j.winH) {
        $img = [System.Drawing.Image]::FromFile($pngPath)
        $bmp = New-Object System.Drawing.Bitmap($img)
        $outPath = Join-Path $outRoot $j.out
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outPath) | Out-Null
        "{0,-22} render {1}x{2}px" -f $j.html, $img.Width, $img.Height
        $info = Trim-ToBox -Bmp $bmp -PadPx $j.padPx -BoxW $j.paddedW -BoxH $j.paddedH -OutPath $outPath
        "  -> {0}  {1}x{2}px   {3}" -f $j.out, $j.paddedW, $j.paddedH, $info
        $bmp.Dispose(); $img.Dispose()
    }
}
