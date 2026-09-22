<template>
  <view class="ys-notice">
    <image class="ys-notice__icon" :src="icon" mode="aspectFit" />
    <text class="ys-notice__text">{{ text || placeholder }}</text>
  </view>
</template>

<script setup>
/**
 * 首页公告栏
 *
 * 实测（原型 home.html）：高 34.67pt、圆角 8pt、底色由 #E3EEF1 渐变到 #EAF4F6；
 * 喇叭图标 22×22pt（雪碧图切出，已落到 static/images/home/notice-icon.png）；
 * 文案 14pt #527180。原型截图停在「暂无公告」的中间态，因此空文案时显示「暂无公告」。
 */
defineProps({
  text: { type: String, default: '' },
  placeholder: { type: String, default: '暂无公告' },
  icon: { type: String, default: '/static/images/home/notice-icon.png' }
})
</script>

<style lang="scss" scoped>
.ys-notice {
  position: relative;
  display: flex;
  align-items: center;
  height: 67rpx;
  border-radius: $ys-radius-sm;
  background: linear-gradient(180deg, #e3eef1 0%, #eaf4f6 100%);

  &__icon {
    width: 42rpx;
    height: 42rpx;
    margin-left: 19rpx;
    flex: none;
  }

  &__text {
    margin-left: 12rpx;
    font-size: 27rpx;
    color: #527180;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
