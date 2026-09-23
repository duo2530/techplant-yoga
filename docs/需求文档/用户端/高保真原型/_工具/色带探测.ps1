Add-Type -AssemblyName System.Drawing
$dir = Join-Path $PSScriptRoot "..\..\需求文档\用户端\用户端小程序截图"
$out = Join-Path $PSScriptRoot "..\规格\色带分析.txt"
$lines = @()
$lines += "# 截图横向色带分析（源图 1170x2532 @3x，逻辑 390x844）"
$lines += "# 用途：定位照片区，供 HTML 用 background-position 从原图切图（雪碧图法）"
$lines += "# 列：源图y起-止  逻辑y起-止  类型  主色  亮度标准差"
$lines += "# 类型：FLAT=纯色UI区；PHOTO=照片区；MIXD=混合（文字/渐变/图标）"
$lines += ""
Get-ChildItem $dir -Filter *.png | Sort-Object Name | ForEach-Object {
  $bmp = [System.Drawing.Bitmap]::FromFile($_.FullName)
  $lines += "=== $($_.Name)  [$($bmp.Width)x$($bmp.Height)]"
  $prev = $null; $runStart = 0; $lastStd = 0
  for ($y = 0; $y -lt $bmp.Height; $y += 6) {
    $sr=0;$sg=0;$sb=0;$n=0;$sum=0;$sum2=0
    for ($x = 0; $x -lt $bmp.Width; $x += 6) {
      $c = $bmp.GetPixel($x,$y); $sr+=$c.R;$sg+=$c.G;$sb+=$c.B;$n++
      $l = 0.299*$c.R + 0.587*$c.G + 0.114*$c.B; $sum+=$l; $sum2+=$l*$l
    }
    $mr=[int]($sr/$n);$mg=[int]($sg/$n);$mb=[int]($sb/$n)
    $mean=$sum/$n; $var=($sum2/$n)-($mean*$mean); if($var -lt 0){$var=0}; $std=[math]::Round([math]::Sqrt($var),1)
    $cls = if ($std -lt 7) {"FLAT"} elseif ($std -gt 24) {"PHOTO"} else {"MIXD"}
    $hex = "#{0:X2}{1:X2}{2:X2}" -f $mr,$mg,$mb
    $cur = "$cls|$hex"
    if ($prev -eq $null) { $prev=$cur; $runStart=$y; $lastStd=$std }
    else {
      $p=$prev.Split('|')
      $d=[math]::Abs([Convert]::ToInt32($p[1].Substring(1,2),16)-$mr)+[math]::Abs([Convert]::ToInt32($p[1].Substring(3,2),16)-$mg)+[math]::Abs([Convert]::ToInt32($p[1].Substring(5,2),16)-$mb)
      if ($p[0] -ne $cls -or $d -gt 30) {
        if (($y-$runStart) -ge 12) {
          $lines += ("  {0,4}-{1,4}  {2,3}-{3,3}pt  {4}  {5}  sd={6}" -f $runStart,($y-6),([int]($runStart/3)),([int](($y-6)/3)),$p[0],$p[1],$lastStd)
        }
        $prev=$cur; $runStart=$y
      }
      $lastStd=$std
    }
  }
  $lines += ""
  $bmp.Dispose()
}
$lines | Set-Content -Path $out -Encoding UTF8
"written: $out  ($($lines.Count) lines)"
