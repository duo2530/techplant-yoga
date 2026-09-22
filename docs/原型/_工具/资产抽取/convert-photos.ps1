# Re-export photo assets as JPEG (quality 80, max width 750px) for mini-program bundle size.
# Also converts the share poster.  Deletes the superseded .png files.
Add-Type -AssemblyName System.Drawing

# 仓库根：从脚本位置向上找到含 frontend\uni-app 的目录
# （本脚本位于 docs\原型\_工具\资产抽取\，位置变化也能正确定位）
$root = $PSScriptRoot
while ($root -and -not (Test-Path (Join-Path $root 'frontend\uni-app'))) { $root = Split-Path -Parent $root }
$srcDir  = Join-Path $root 'docs\需求文档\用户端\用户端小程序截图'
$outRoot = Join-Path $root 'frontend\uni-app\static\images'

$JpegCodec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
             Where-Object { $_.MimeType -eq 'image/jpeg' }

function Save-Jpeg {
    param([System.Drawing.Image]$Img,[string]$OutPath,[int]$Quality)
    $ps = New-Object System.Drawing.Imaging.EncoderParameters(1)
    $ps.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, [long]$Quality)
    $Img.Save($OutPath, $JpegCodec, $ps)
    $ps.Dispose()
}

# Convert an existing PNG in the output tree to JPEG, then delete the PNG.
function Convert-PngToJpeg {
    param([string]$Rel,[int]$MaxWidth = 750,[int]$Quality = 80)
    $inPath  = Join-Path $outRoot $Rel
    if (-not (Test-Path $inPath)) { "[SKIP missing] $Rel"; return }
    $outPath = [System.IO.Path]::ChangeExtension($inPath, '.jpg')
    $img = [System.Drawing.Image]::FromFile($inPath)
    $w = $img.Width; $h = $img.Height
    if ($w -gt $MaxWidth) { $h = [int][Math]::Round($h * $MaxWidth / $w); $w = $MaxWidth }
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $bmp.SetResolution(72, 72)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($img, (New-Object System.Drawing.Rectangle(0,0,$w,$h)))
    $g.Dispose()
    Save-Jpeg -Img $bmp -OutPath $outPath -Quality $Quality
    $old = $img.Width; $oldH = $img.Height
    $bmp.Dispose(); $img.Dispose()
    Remove-Item $inPath -Force
    "{0,-30} {1}x{2} png -> {3}x{4} jpg q{5}" -f $Rel, $old, $oldH, $w, $h, $Quality
}

$photos = @(
  'home/store-hero.png',
  'home/banner-scene.png',
  'home/coach1-photo.png','home/coach2-photo.png','home/coach3-photo.png',
  'home/coach1-avatar.png','home/coach2-avatar.png','home/coach3-avatar.png',
  'store/photo-1.png','store/photo-2.png','store/photo-3.png',
  'mine/header-bg.png','mine/avatar.png'
)
foreach ($rel in $photos) { Convert-PngToJpeg -Rel $rel -MaxWidth 750 -Quality 80 }

# --- share poster: whole screenshot -> JPEG ---
$posterSrc = Join-Path $srcDir '二级页面-分享.png'
$posterOut = Join-Path $outRoot 'share\poster.jpg'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $posterOut) | Out-Null
$img = [System.Drawing.Image]::FromFile($posterSrc)
$w = $img.Width; $h = $img.Height
if ($w -gt 750) { $h = [int][Math]::Round($h * 750 / $w); $w = 750 }
$bmp = New-Object System.Drawing.Bitmap($w, $h)
$bmp.SetResolution(72, 72)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.DrawImage($img, (New-Object System.Drawing.Rectangle(0,0,$w,$h)))
$g.Dispose()
Save-Jpeg -Img $bmp -OutPath $posterOut -Quality 82
"{0,-30} {1}x{2} png -> {3}x{4} jpg q82" -f 'share/poster.jpg', $img.Width, $img.Height, $w, $h
$bmp.Dispose(); $img.Dispose()

# --- remove leftover RuoYi scaffold tabbar icons ---
foreach ($n in @('home_.png','mine.png','mine_.png','work.png','work_.png')) {
    $p = Join-Path $outRoot "tabbar\$n"
    if (Test-Path $p) { Remove-Item $p -Force; "removed tabbar/$n" } else { "already absent tabbar/$n" }
}
