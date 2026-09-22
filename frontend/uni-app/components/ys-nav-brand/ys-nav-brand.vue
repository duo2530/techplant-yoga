<template>
  <view class="ys-nav-brand" :style="{ paddingTop: statusBarHeight + 'px' }">
    <view class="ys-nav-brand__bar">
      <image class="ys-nav-brand__logo" src="/static/images/brand/nav-logo.png" mode="aspectFit" />
      <text class="ys-nav-brand__title">{{ title }}</text>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

/**
 * 首页自定义导航栏（品牌字 + 书法「一」Logo，整体左对齐）
 *
 * 原型实测：Logo 逻辑 x 13.0..26.7（14×21pt）、品牌字 34.0..133.7pt、17pt 字重 500 字距 1.5pt；
 * 页面中心是 195pt，所以是**左对齐**而不是居中。微信胶囊按钮由系统绘制在右侧，这里不画。
 *
 * 首页在 pages.json 里是 `navigationStyle: custom`，所以状态栏高度要自己补。
 */
defineProps({
  title: { type: String, default: '一水·瑜伽普拉提' }
})

const statusBarHeight = ref(0)
const systemInfo = uni.getSystemInfoSync()
statusBarHeight.value = systemInfo.statusBarHeight || 0
</script>

<style lang="scss" scoped>
.ys-nav-brand {
  background: #fff;

  &__bar {
    display: flex;
    align-items: center;
    height: 88rpx;
    padding-left: 25rpx;
  }

  &__logo {
    width: 27rpx;
    height: 40rpx;
    flex: none;
  }

  &__title {
    margin-left: 13rpx;
    font-size: 33rpx;
    font-weight: 500;
    letter-spacing: 3rpx;
    color: $ys-ink-1;
  }
}
</style>
