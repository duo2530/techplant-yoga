# 渲染原型页并与原始截图合成左右对比图
# 用法： pwsh -NoProfile -File ".\docs\原型\_工具\渲染对比.ps1" [-Only home.html]
param([string]$Only = "")

Add-Type -AssemblyName System.Drawing

$Root   = Resolve-Path (Join-Path $PSScriptRoot "..\..\..")
$Proto  = Join-Path $Root "docs\原型"
$Shots  = Join-Path $Root "docs\需求文档\小程序截图"
$OutDir = Join-Path $PSScriptRoot "对比"
$TmpDir = Join-Path $PSScriptRoot "渲染"
New-Item -ItemType Directory -Force -Path $OutDir, $TmpDir | Out-Null

$Chrome = "C:\Program Files\Google\Chrome\Application\chrome.exe"
# 每次用独立 profile，避免与并发的子代理抢 Chrome 导致失败
$Prof   = Join-Path $env:TEMP ("dsh-chrome-" + [guid]::NewGuid().ToString('N'))

$MAP = [ordered]@{
  'home.html'            = '首页1.png'
  'booking.html'         = '约课.png'
  'my-bookings.html'     = '我的预约.png'
  'profile.html'         = '我的页面.png'
  'trial-apply.html'     = '二级页面-申请体验.png'
  'my-trials.html'       = '我的体验课.png'
  'share-poster.html'    = '二级页面-分享.png'
  'activities.html'      = '二级页面-活动列表.png'
  'coaches.html'         = '二级页面-约教练.png'
  'messages.html'        = '消息.png'
  'terms.html'           = '用户协议.png'
  'privacy.html'         = '隐私协议.png'
  # 主状态取「登录页.png」（无遮罩、元素全可见）；「快速登录.png」是弹层态
  'quick-login.html'     = '登录页.png'
  'phone-auth.html'      = '手机登录页.png'
  'forgot-password.html' = '忘记密码页.png'
  'native-map.html'      = '地图.png'
  'native-location.html' = '请求位置信息.png'
}

$H = 1500   # 统一缩放高度

foreach ($page in $MAP.Keys) {
  if ($Only -ne "" -and $page -ne $Only) { continue }
  $pagePath = Join-Path $Proto $page
  if (-not (Test-Path $pagePath)) { "SKIP (不存在): $page"; continue }

  $shotPath  = Join-Path $Shots $MAP[$page]
  $renderPng = Join-Path $TmpDir ($page -replace '\.html$', '.png')

  $u = ([System.Uri]$pagePath).AbsoluteUri + "?shot=1"
  if (Test-Path $renderPng) { Remove-Item $renderPng -Force }
  $err = ""
  for ($try = 1; $try -le 2; $try++) {
    $err = & $Chrome --headless=new --disable-gpu --hide-scrollbars --no-first-run `
      --user-data-dir="$Prof" --virtual-time-budget=7000 `
      --window-size=390,844 --screenshot="$renderPng" $u 2>&1 | Out-String
    if (Test-Path $renderPng) { break }
    Start-Sleep -Milliseconds 900
  }
  if (-not (Test-Path $renderPng)) {
    "FAIL 渲染: $page"
    ($err -split "`r?`n" | Where-Object { $_.Trim() -ne "" } | Select-Object -First 3) | ForEach-Object { "     $_" }
    continue
  }

  # 左 = 原截图，右 = 渲染
  $a = [System.Drawing.Bitmap]::FromFile($shotPath)
  $b = [System.Drawing.Bitmap]::FromFile($renderPng)
  $aw = [int]($a.Width * $H / $a.Height)
  $bw = [int]($b.Width * $H / $b.Height)
  $gap = 24
  $canvas = New-Object System.Drawing.Bitmap ($aw + $gap + $bw), $H
  $g = [System.Drawing.Graphics]::FromImage($canvas)
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.Clear([System.Drawing.Color]::FromArgb(27, 31, 39))
  $g.DrawImage($a, 0, 0, $aw, $H)
  $g.DrawImage($b, ($aw + $gap), 0, $bw, $H)
  $g.Dispose()

  $outFile = Join-Path $OutDir ($page -replace '\.html$', '.png')
  $canvas.Save($outFile, [System.Drawing.Imaging.ImageFormat]::Png)
  $canvas.Dispose(); $a.Dispose(); $b.Dispose()

  "OK  $page"
}
