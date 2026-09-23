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

/**
 * 取状态栏高度
 *
 * 用 `uni.getWindowInfo()`（微信新版 API）而不是已废弃的 `uni.getSystemInfoSync()`：
 * 后者在较新的微信基础库（lib 3.16.x）里被标记废弃，个别版本上会在内部读取
 * `errMsg` 时抛 `Cannot read property 'errMsg' of undefined`（真机/开发者工具都出现过）。
 * 老库或非微信端没有 getWindowInfo 时回退到 getSystemInfoSync，并做空值兜底。
 */
const windowInfo = (typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()) || {}
statusBarHeight.value = windowInfo.statusBarHeight || 0
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
