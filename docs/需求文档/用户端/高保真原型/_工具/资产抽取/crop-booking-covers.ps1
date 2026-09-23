# Crop the two booking-card cover photos from 约课.png.
# Coordinates come from the prototype comments in docs/原型/booking.html:
#   卡片1 封面: 源图 x=90..345, y=798..1053  -> 逻辑 30..115, 266..351
#   卡片3 封面: 源图 x=90..345, y=1596..1851 -> 逻辑 30..115, 532..617
# (卡片2 与 卡片3 在原型里引用同一张照片，故只需两张)
Add-Type -AssemblyName System.Drawing

# 仓库根：从脚本位置向上找到含 frontend\uni-app 的目录
# （本脚本位于 docs\原型\_工具\资产抽取\，位置变化也能正确定位）
$root = $PSScriptRoot
while ($root -and -not (Test-Path (Join-Path $root 'frontend\uni-app'))) { $root = Split-Path -Parent $root }
$srcPath = Join-Path $root 'docs\需求文档\用户端\用户端小程序截图\约课.png'
$outRoot = Join-Path $root 'frontend\uni-app\static\images'

$JpegCodec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
             Where-Object { $_.MimeType -eq 'image/jpeg' }

function Crop-Jpeg {
    param([string]$Out,[double]$X,[double]$Y,[double]$W,[double]$H,[int]$Quality = 85)
    $px=[int][Math]::Round($X*3); $py=[int][Math]::Round($Y*3)
    $pw=[int][Math]::Round($W*3); $ph=[int][Math]::Round($H*3)
    $img = [System.Drawing.Image]::FromFile($srcPath)
    if($px -lt 0 -or $py -lt 0 -or ($px+$pw) -gt $img.Width -or ($py+$ph) -gt $img.Height){
        Write-Warning ("OUT OF BOUNDS {0} rect=({1},{2},{3},{4}) img={5}x{6}" -f $Out,$px,$py,$pw,$ph,$img.Width,$img.Height)
    }
    $rect = New-Object System.Drawing.Rectangle($px,$py,$pw,$ph)
    $bmp  = New-Object System.Drawing.Bitmap($pw,$ph)
    $bmp.SetResolution(72,72)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($img,(New-Object System.Drawing.Rectangle(0,0,$pw,$ph)),$rect,[System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $outPath = Join-Path $outRoot $Out
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outPath) | Out-Null
    $ps = New-Object System.Drawing.Imaging.EncoderParameters(1)
    $ps.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, [long]$Quality)
    $bmp.Save($outPath, $JpegCodec, $ps)
    $ps.Dispose(); $bmp.Dispose(); $img.Dispose()
    "{0,-26} 约课.png  src({1},{2},{3},{4})  -> {5}x{6} jpg q{7}" -f $Out,$px,$py,$pw,$ph,$pw,$ph,$Quality
}

Crop-Jpeg 'booking/cover-1.jpg' 30 266 85 85 85
Crop-Jpeg 'booking/cover-2.jpg' 30 532 85 85 85
