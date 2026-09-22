# Crop all design-image assets.  Source = design spec coordinates (logical pt).
# Source pixels = logical pt * 3.
Add-Type -AssemblyName System.Drawing

# 仓库根：从脚本位置向上找到含 frontend\uni-app 的目录
# （本脚本位于 docs\原型\_工具\资产抽取\，位置变化也能正确定位）
$root = $PSScriptRoot
while ($root -and -not (Test-Path (Join-Path $root 'frontend\uni-app'))) { $root = Split-Path -Parent $root }
$srcDir  = Join-Path $root 'docs\需求文档\用户端\用户端小程序截图'
$outRoot = Join-Path $root 'frontend\uni-app\static\images'

function Crop {
    param([string]$SrcFile,[double]$X,[double]$Y,[double]$W,[double]$H,[string]$Out)
    $srcPath = Join-Path $srcDir $SrcFile
    $outPath = Join-Path $outRoot $Out
    $outDir  = Split-Path -Parent $outPath
    if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
    $px=[int][Math]::Round($X*3); $py=[int][Math]::Round($Y*3)
    $pw=[int][Math]::Round($W*3); $ph=[int][Math]::Round($H*3)
    $img=[System.Drawing.Image]::FromFile($srcPath)
    if($px -lt 0 -or $py -lt 0 -or ($px+$pw) -gt $img.Width -or ($py+$ph) -gt $img.Height){
        Write-Warning ("OUT OF BOUNDS {0} rect=({1},{2},{3},{4}) img={5}x{6}" -f $Out,$px,$py,$pw,$ph,$img.Width,$img.Height)
    }
    $rect = New-Object System.Drawing.Rectangle($px,$py,$pw,$ph)
    $bmp  = New-Object System.Drawing.Bitmap($pw,$ph)
    $bmp.SetResolution(72,72)
    $g    = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode  =[System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($img,(New-Object System.Drawing.Rectangle(0,0,$pw,$ph)),$rect,[System.Drawing.GraphicsUnit]::Pixel)
    $bmp.Save($outPath,[System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose();$bmp.Dispose();$img.Dispose()
    "{0,-34} {1,-16} x={2,-7} y={3,-7} w={4,-6} h={5,-6} -> {6}x{7}px" -f $Out,$SrcFile,$X,$Y,$W,$H,$pw,$ph
}

# ============ home ============
Crop '首页1.png' 13     90.67   364    166    'home/store-hero.png'
Crop '首页1.png' 23     273     22     22     'home/notice-icon.png'
Crop '首页1.png' 22     334     30     30     'home/icon-group.png'
Crop '首页1.png' 208    334     30     30     'home/icon-private.png'
Crop '首页1.png' 20     405     22     22     'home/icon-trial.png'
Crop '首页1.png' 145    405     22     22     'home/icon-checkin.png'
Crop '首页1.png' 268    405     22     22     'home/icon-activity.png'
Crop '首页1.png' 126.67 670     56     30     'home/empty-hot-course.png'
Crop '首页2.png' 13     500.67  364    257    'home/banner-scene.png'
Crop '首页2.png' 233.33 139.67  143.33 108.67 'home/coach1-photo.png'
Crop '首页2.png' 24.33  154     79     79     'home/coach1-avatar.png'
Crop '首页2.png' 233.33 257.67  143.33 108.67 'home/coach2-photo.png'
Crop '首页2.png' 24.33  272     79     79     'home/coach2-avatar.png'
Crop '首页2.png' 233.33 375.67  143.33 108.67 'home/coach3-photo.png'
Crop '首页2.png' 24.33  390     79     79     'home/coach3-avatar.png'

# ============ store ============
Crop '首页3.png' 13 98  364 243 'store/photo-1.png'
Crop '首页3.png' 13 350 364 243 'store/photo-2.png'
Crop '首页3.png' 13 602 364 156 'store/photo-3.png'

# ============ brand / mine ============
Crop '首页1.png'   13 61  14  21  'brand/nav-logo.png'
Crop '我的页面.png' 0  44  390 160 'mine/header-bg.png'
Crop '我的页面.png' 38 93  52  52  'mine/avatar.png'

# ============ empty states ============
Crop '我的预约.png'   152 382 87 64 'empty/booking.png'
Crop '我的体验课.png' 153 235 83 83 'empty/trial.png'

# ============ auth ============
Crop '登录页.png' 43.3 140 303.3 170 'auth/logo-block.png'

# ============ tabbar: 30x30 pt squares centred on each tab icon ============
$tabY = 761.5; $tabSize = 30
$tabList = @(
    @{ n='home';    c=48.75  },
    @{ n='booking'; c=146.25 },
    @{ n='booked';  c=243.75 },
    @{ n='me';      c=341.25 }
)
foreach ($t in $tabList) {
    $x = [double]$t.c - 15.0
    $n = [string]$t.n
    if ($n -eq 'home')    { $unsel = '约课.png';    $sel = '首页1.png' }
    if ($n -eq 'booking') { $unsel = '首页1.png';   $sel = '约课.png' }
    if ($n -eq 'booked')  { $unsel = '首页1.png';   $sel = '我的预约.png' }
    if ($n -eq 'me')      { $unsel = '首页1.png';   $sel = '我的页面.png' }
    Crop $unsel $x $tabY $tabSize $tabSize "tabbar/$n.png"
    Crop $sel   $x $tabY $tabSize $tabSize "tabbar/$n-sel.png"
}
