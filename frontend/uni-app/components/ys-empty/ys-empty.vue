<template>
  <view class="ys-empty" :style="{ paddingTop: paddingTop + 'rpx' }">
    <!-- 插画：原型里不同页面的空态插画不同（有的来自截图资产，有的是原型画的 SVG） -->
    <image
      v-if="image"
      class="ys-empty__img"
      :src="image"
      :style="imgStyle"
      mode="aspectFit"
    />
    <view class="ys-empty__title" :style="{ color: titleColor }">{{ title }}</view>
    <view v-if="subtitle" class="ys-empty__subtitle">{{ subtitle }}</view>
    <slot></slot>
  </view>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 空态组件
 *
 * 原型里多处是空态（复刻规范 §1.2：空态也要复刻，不要塞假数据），
 * 各页面的插画/文案/颜色不同，这里统一成一个组件：
 *  - image / imageWidth / imageHeight：插画与尺寸（各页实测值不同，由页面传入）
 *  - title / subtitle / titleColor：文案与颜色
 *  - paddingTop：整块距内容区顶部的距离
 */
const props = defineProps({
  image: { type: String, default: '' },
  imageWidth: { type: String, default: '' },
  imageHeight: { type: String, default: '' },
  title: { type: String, default: '暂无数据' },
  subtitle: { type: String, default: '' },
  titleColor: { type: String, default: '#AAAAAA' },
  paddingTop: { type: [Number, String], default: 0 }
})

const imgStyle = computed(() => {
  const style = {}
  if (props.imageWidth) {
    style.width = props.imageWidth
  }
  if (props.imageHeight) {
    style.height = props.imageHeight
  }
  return style
})
</script>

<style lang="scss" scoped>
.ys-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;

  &__img {
    display: block;
  }

  &__title {
    margin-top: 25rpx;
    font-size: 29rpx;
    line-height: 1;
  }

  &__subtitle {
    margin-top: 4rpx;
    font-size: 25rpx;
    line-height: 1;
    color: $ys-ink-3;
  }
}
</style>
