<template>
  <view class="poster">
    <!-- 整屏海报：原图 750×1623px，750rpx 宽正好 1:1；widthFix 让高度按原比例走，
         不拉伸、不裁切（海报纸边缘自带深色底，短屏机型底部露出的背景色也按黑处理） -->
    <image class="poster__img" src="/static/images/share/poster.jpg" mode="widthFix" />
  </view>
</template>

<script setup>
/** 分享海报 */
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
