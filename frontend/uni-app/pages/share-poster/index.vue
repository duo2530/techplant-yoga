<template>
  <view class="poster">
    <!-- 整屏海报：原图 750×1623px，750rpx 宽正好 1:1；widthFix 让高度按原比例走，
         不拉伸、不裁切（海报纸边缘自带深色底，短屏机型底部露出的背景色也按黑处理） -->
    <image class="poster__img" src="/static/images/share/poster.jpg" mode="widthFix" />
  </view>
</template>

<script setup>
/**
 * 分享海报（pages.json 标题「分享海报」，且本页是 `navigationStyle: "custom"`）
 *
 * 照 docs/原型/share-poster.html 还原：**整屏就是一张海报大图**。
 *
 * 【TODO —— 原型阶段的取巧，真实上线前必须处理】
 * 现在的底图 `static/images/share/poster.jpg` 就是「二级页面-分享.png」整张截图：
 * **状态栏、导航栏（‹ ⌂ 描边胶囊 + 微信胶囊）、品牌字「【一水·瑜伽普拉提】/ 练瑜伽普拉提就来一水」、
 * 正文三行文案、右下角小程序码、底部「下载 / 分享」操作栏，全部烘焙在图里**。
 * 这是原型阶段的取巧，不是可以上线的实现：
 *   1. 需要设计师给一张**不带手机 UI 的海报原稿**（不含状态栏 / 导航栏 / 微信胶囊 / 底部操作栏，
 *      小程序码另行提供）；小程序码也应该由后端生成带参码（扫码进入指定门店/课程），
 *      而不是把当前这张固定码烤在图片里。
 *   2. 拿到原稿后，本页要**自绘底部操作栏**（「下载」= 保存海报到相册，需 uni.saveImageToPhotosAlbum +
 *      scope.writePhotosAlbum 授权；「分享」= 转发给好友/生成分享图，按钮需 open-type="share" 或
 *      调用原生分享），这些热区现在都还在图里，页面代码里没有任何点击逻辑。
 *   3. 原型的轮播（1 段长条 + 1 个圆点 = 2 页）目前只拿到第 1 页海报，**不编造第 2 张设计稿**，
 *      所以这里只渲染一张整屏海报，未实现滑动分页与页码指示器。
 * 在上面这些信息（海报原稿、小程序码、底部操作栏设计）拿到之前，**不自己编造**栏位与按钮。
 *
 * 本页 navigationStyle 是 custom，所以不会被原生导航栏占掉高度，铺满整屏。
 */
</script>

<style lang="scss" scoped>
/* 海报图自带深色底，页面背景也按黑处理，避免短屏机型底部露白 */
page {
  background: #000000;
}

.poster {
  min-height: 100vh;
  background: #000000;
}

.poster__img {
  display: block;
  width: 750rpx;
}
</style>
