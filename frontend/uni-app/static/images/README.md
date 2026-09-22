# `static/images` 资产清单

从设计稿原始截图裁切 / 按原型 SVG 栅格化得到的图片资产。

- **换算公式：源图像素 = 逻辑值(pt) × 3**（截图 1170×2532 = iPhone @3x，逻辑画布 390×844）。
- 原始截图位于 `docs/需求文档/用户端/用户端小程序截图/`，**只读取，未做任何修改**。
- 裁切脚本：`docs/原型/_工具/资产抽取/crop-assets.ps1`；照片转 JPEG：`docs/原型/_工具/资产抽取/convert-photos.ps1`；
  空态 SVG 栅格化：`docs/原型/_工具/资产抽取/rasterize-empty.ps1`（临时页 `docs/原型/_工具/渲染/_empty-*.html`）。
  > 脚本已归入工程既有工具目录 `docs/原型/_工具/资产抽取/`（含裁切/转码/栅格化三个入口）。
- 全部共 **45 个文件，合计约 0.645 MB**（若依脚手架遗留的 `profile.jpg`、`banner/banner0{1,2,3}.jpg` 已删除）。
  > `auth/` 下的 7 个图标（checkbox / icon-code / icon-eye / icon-lock / icon-phone）
  > 由并行工作加入。以上均非本次任务产出。

## 格式约定

- **PNG**：小图标 / 线稿 / 插画（需要清晰边缘，且体积本来就小）。
- **JPEG（质量 80，最大宽 750px）**：照片类资产。小程序主包上限 2MB，
  照片按 PNG 存会达到 ~6.5MB 超限，故转 JPEG。
- `share/poster.jpg` 用质量 82。

---

## 一、首页（源图 `首页1.png` / `首页2.png`）

| 输出文件 | 用途 | 来源截图 | 逻辑矩形 (x, y, w, h) | 输出像素 |
|---|---|---|---|---|
| `home/store-hero.jpg` | 门店头图（大照片） | 首页1.png | 13, 90.67, 364, 166 | 750×342 |
| `home/notice-icon.png` | 公告栏蓝色喇叭圆图标 | 首页1.png | 23, 273, 22, 22 | 66×66 |
| `home/icon-group.png` | 团课预约图标 | 首页1.png | 22, 334, 30, 30 | 90×90 |
| `home/icon-private.png` | 私教预约图标 | 首页1.png | 208, 334, 30, 30 | 90×90 |
| `home/icon-trial.png` | 体验课图标 | 首页1.png | 20, 405, 22, 22 | 66×66 |
| `home/icon-checkin.png` | 我要打卡图标 | 首页1.png | 145, 405, 22, 22 | 66×66 |
| `home/icon-activity.png` | 活动专区图标 | 首页1.png | 268, 405, 22, 22 | 66×66 |
| `home/empty-hot-course.png` | 热门课程空态插画 | 首页1.png | 126.67, 670, 56, 30 | 168×90 |
| `home/banner-scene.jpg` | 场景横幅大图 | 首页2.png | 13, 500.67, 364, 257 | 750×530 |
| `home/coach1-photo.jpg` | 教练1 卡片右侧装饰图 | 首页2.png | 233.33, 139.67, 143.33, 108.67 | 430×326 |
| `home/coach1-avatar.jpg` | 教练1 方形头像 | 首页2.png | 24.33, 154, 79, 79 | 237×237 |
| `home/coach2-photo.jpg` | 教练2 卡片右侧装饰图 | 首页2.png | 233.33, 257.67, 143.33, 108.67 | 430×326 |
| `home/coach2-avatar.jpg` | 教练2 方形头像 | 首页2.png | 24.33, 272, 79, 79 | 237×237 |
| `home/coach3-photo.jpg` | 教练3 卡片右侧装饰图 | 首页2.png | 233.33, 375.67, 143.33, 108.67 | 430×326 |
| `home/coach3-avatar.jpg` | 教练3 方形头像 | 首页2.png | 24.33, 390, 79, 79 | 237×237 |

> `coach*-photo` 命名沿用任务清单，但**实际内容是卡片右侧的浅蓝装饰插画**（禅舞人形轮廓），
> 不是教练照片——原始截图里教练卡片右侧本来就是这块装饰图，已实拍核对（见「核对说明」）。

## 二、门店实景（源图 `首页3.png`）

| 输出文件 | 用途 | 来源截图 | 逻辑矩形 | 输出像素 |
|---|---|---|---|---|
| `store/photo-1.jpg` | 门店实景 1 | 首页3.png | 13, 98, 364, 243 | 750×501 |
| `store/photo-2.jpg` | 门店实景 2 | 首页3.png | 13, 350, 364, 243 | 750×501 |
| `store/photo-3.jpg` | 门店实景 3 | 首页3.png | 13, 602, 364, 156 | 750×321 |

## 三、品牌与我的

| 输出文件 | 用途 | 来源截图 | 逻辑矩形 | 输出像素 |
|---|---|---|---|---|
| `brand/nav-logo.png` | 导航栏书法「一」Logo | 首页1.png | 13, 61, 14, 21 | 42×63 |
| `mine/header-bg.jpg` | 我的页顶部背景图 | 我的页面.png | 0, 44, 390, 160 | 750×308 |
| `mine/avatar.jpg` | 圆形头像（方图，页面里圆裁） | 我的页面.png | 38, 93, 52, 52 | 156×156 |

## 四、空态插画

| 输出文件 | 用途 | 来源 | 逻辑矩形 / 画布 | 输出像素 |
|---|---|---|---|---|
| `empty/booking.png` | 我的预约空态插画 | 我的预约.png | 152, 382, 87, 64 | 261×192 |
| `empty/trial.png` | 我的体验课空态插画 | 我的体验课.png | 153, 235, 83, 83 | 249×249 |
| `empty/activity.png` | 活动列表空态图标 | **栅格化自 `docs/原型/activities.html` 第 52–61 行 SVG** | 39×36 pt（非裁切） | 117×108 |
| `empty/message.png` | 消息页空态插图 | **栅格化自 `docs/原型/messages.html` 第 61–74 行 SVG** | 66×52 pt（非裁切） | 198×156 |

`empty/activity.png` 与 `empty/message.png` 是**照原型 SVG 1:1 绘制后栅格化**（原型里是内联 SVG，
小程序不支持，故转位图）：

- 用 headless Chrome 渲染临时页（`--force-device-scale-factor=3`、`--default-background-color=00000000`）后裁到 SVG 画布。
- **透明底 PNG（32 位带 Alpha）**，比白底更好用；直接放在页面的渐变背景上即可。
- `activity.png`：`stroke="#AAAAAA"` `stroke-width="2.25"`（细条 2.5），圆角方框 + 三根竖条 + 右下带减号圆。
  实测 ink bbox `x3..114 y3..104`，**外描边未被裁切**（形体本身在 viewBox 内有内边距）。
- `message.png`：山丘 `#E9E9E9`、飞碟 `#8E8E8E`、三朵云 `#DCDCDC`、图片占位 `#BDBDBD` + 白色山峰/太阳。
  实测 ink bbox `x0..197 y31..155`（SVG 画布顶部 31px 为设计留白）。

## 五、登录页

| 输出文件 | 用途 | 来源截图 | 逻辑矩形 | 输出像素 |
|---|---|---|---|---|
| `auth/logo-block.png` | 登录页品牌 Logo 区块 | 登录页.png | 43.3, 140, 303.3, 170 | 910×510 |

## 五之二、约课页卡片封面（源图 `约课.png`）

| 输出文件 | 用途 | 逻辑矩形 | 输出像素 |
|---|---|---|---|
| `booking/cover-1.jpg` | 约课卡片 1 封面 | 30, 270, 85, 85 | 255×255 |
| `booking/cover-2.jpg` | 约课卡片 2、3 封面（同一张照片） | 30, 534, 85, 85 | 255×255 |

坐标来自原型 `docs/原型/booking.html` 注释（卡片 1：`x=90..345, y=798..1053`；
卡片 3：`x=90..345, y=1596..1851`），但**实测截图里照片像素比原型值低约 4pt**：

- 照片实际范围：卡片 1 = 逻辑 `x30..115, y270..354`；卡片 3 = 逻辑 `x30..115, y534..619`。
- 若照抄原型的 y=266/532，会**多带约 4pt 卡片白底、并切掉约 3pt 照片下沿**；
  故 y 下移 4pt（266→270、532→534）让裁切**贴合照片**。x 与原型/任务清单一致。
- 原型对卡片 3 自己标注的 y 也是 **534**（`y=1602..1857`），与此处采用的 534 一致——
  即任务清单里的 `y=532` 是原型早先的估值。
- 竖版照片高约 84–85pt，为满足「85×85pt 方图」，竖向各含约 0.5pt 边缘（不切主体）。
- JPEG 质量 85，未缩放（85pt×3 = 255px 正好）。


## 六、分享海报

| 输出文件 | 用途 | 来源截图 | 输出像素 |
|---|---|---|---|
| `share/poster.jpg` | 分享海报整屏（含状态栏 / 二维码 / 底部操作栏） | 二级页面-分享.png（整张，1170×2532） | 750×1623 |

## 七、TabBar 图标（8 张）

每个 Tab 裁 **30×30 逻辑 pt**（源图 90×90px）方形，中心对准 Tab 图标中心：
x 从 `中心x - 15` 到 `中心x + 15`，y 从 **761.5** 到 **791.5**。
四个 Tab 中心逻辑 x：**48.75 / 146.25 / 243.75 / 341.25**。
截图里 TabBar 背景纯白，故输出为白底图标，可直接用于小程序原生 tabBar。

| 输出文件 | 状态 | 来源截图 | 逻辑矩形 | 输出像素 |
|---|---|---|---|---|
| `tabbar/home.png` | 未选中（灰 `#949494` 描边） | 约课.png | 33.75, 761.5, 30, 30 | 90×90 |
| `tabbar/home-sel.png` | 选中（蓝实心） | 首页1.png | 33.75, 761.5, 30, 30 | 90×90 |
| `tabbar/booking.png` | 未选中 | 首页1.png | 131.25, 761.5, 30, 30 | 90×90 |
| `tabbar/booking-sel.png` | 选中 | 约课.png | 131.25, 761.5, 30, 30 | 90×90 |
| `tabbar/booked.png` | 未选中 | 首页1.png | 228.75, 761.5, 30, 30 | 90×90 |
| `tabbar/booked-sel.png` | 选中 | 我的预约.png | 228.75, 761.5, 30, 30 | 90×90 |
| `tabbar/me.png` | 未选中 | 首页1.png | 326.25, 761.5, 30, 30 | 90×90 |
| `tabbar/me-sel.png` | 选中 | 我的页面.png | 326.25, 761.5, 30, 30 | 90×90 |

实测 8 张的图标 ink 均为 `x20..69 / x18..71`、`y20..73`（水平居中；垂直中心偏低 0.67pt，
因为未选中图标本身高 18pt 而画框 30pt），**无切边、未带入下方 TabBar 文字**。

---

## 变更记录

### 1. 照片类 PNG → JPEG（2026，为解决主包 2MB 超限）

以下 13 个文件的 `.png` 已**删除**，改存同名 `.jpg`（质量 80，最大宽 750px，等比）：

| 原 PNG | 现 JPEG | 原像素 → 新像素 |
|---|---|---|
| `home/store-hero.png` | `home/store-hero.jpg` | 1092×498 → 750×342 |
| `home/banner-scene.png` | `home/banner-scene.jpg` | 1092×771 → 750×530 |
| `home/coach1-photo.png` | `home/coach1-photo.jpg` | 430×326 → 430×326（未超 750，不缩放） |
| `home/coach2-photo.png` | `home/coach2-photo.jpg` | 430×326 → 430×326 |
| `home/coach3-photo.png` | `home/coach3-photo.jpg` | 430×326 → 430×326 |
| `home/coach1-avatar.png` | `home/coach1-avatar.jpg` | 237×237 → 237×237 |
| `home/coach2-avatar.png` | `home/coach2-avatar.jpg` | 237×237 → 237×237 |
| `home/coach3-avatar.png` | `home/coach3-avatar.jpg` | 237×237 → 237×237 |
| `store/photo-1.png` | `store/photo-1.jpg` | 1092×729 → 750×501 |
| `store/photo-2.png` | `store/photo-2.jpg` | 1092×729 → 750×501 |
| `store/photo-3.png` | `store/photo-3.jpg` | 1092×468 → 750×321 |
| `mine/header-bg.png` | `mine/header-bg.jpg` | 1170×480 → 750×308 |
| `mine/avatar.png` | `mine/avatar.jpg` | 156×156 → 156×156 |

**保持 PNG 不变**（小图标 / 线稿 / 插画）：`brand/nav-logo.png`、`home/notice-icon.png`、
`home/icon-*.png`、`home/empty-hot-course.png`、`empty/booking.png`、`empty/trial.png`、
`auth/logo-block.png`、`tabbar/*.png`。

### 2. 新增

- `empty/activity.png`、`empty/message.png`（原型 SVG 栅格化，透明底）。
- `share/poster.jpg`（分享海报整屏，质量 82，最大宽 750px）。
- `booking/cover-1.jpg`、`booking/cover-2.jpg`（约课页卡片封面，质量 85，各 255×255）。

### 3. 删除

- **若依脚手架遗留的 tabBar 图标**（本项目 tabBar 用 `home / booking / booked / me` + `-sel` 共 8 张）：
  `tabbar/home_.png`、`tabbar/mine.png`、`tabbar/mine_.png`、`tabbar/work.png`、`tabbar/work_.png`。

### 4. 体积

| 阶段 | 文件数 | 总大小 |
|---|---|---|
| 照片转 JPEG 前 | 41 | 6.741 MB |
| 照片转 JPEG 后 | 37 | 0.777 MB |
| 加入 2 张空态 + 海报，删除 5 张脚手架图标后 | 39 | 0.783 MB（801.6 KB） |
| 加入 2 张约课卡片封面（`booking/cover-{1,2}.jpg`）后 | 41 | 0.793 MB |
| 目录当前合计（含并行工作加入的 8 个 `auth/` 图标与本 README） | **49** | **0.831 MB（850.9 KB）** |

> 主包 2MB 上限下余量充足，目标 <1.5MB 达成。
> `auth/checkbox-off.png`、`auth/checkbox-on.png`、`auth/icon-code.png`、`auth/icon-eye-off.png`、
> `auth/icon-eye-on.png`、`auth/icon-lock.png`、`auth/icon-phone.png` 共 7 个 PNG
> 不是本次任务的产出（由并行工作加入，未在本文档逐项登记）。

---

## 非本次裁切的既有文件（未改动）

| 文件 | 像素 | 说明 |
|---|---|---|
| `profile.jpg` | 198×198 | 脚手架演示头像（若依模板自带） |
| `banner/banner01.jpg` | 690×270 | 脚手架演示轮播图 |
| `banner/banner02.jpg` | 690×270 | 同上 |
| `banner/banner03.jpg` | 690×270 | 同上 |
