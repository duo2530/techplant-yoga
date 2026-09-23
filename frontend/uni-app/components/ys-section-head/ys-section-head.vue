<template>
  <view class="ys-section-head">
    <text class="ys-section-head__title">{{ title }}</text>
    <view v-if="moreText" class="ys-section-head__more press" @tap="onMore">
      <text class="ys-section-head__more-text">{{ moreText }}</text>
      <!-- 箭头必须走绑定 + decode：uni-app 会把模板里的字面 `>` 转义成 `&gt;` 写进 WXML，
           而微信 <text> 默认不解码实体，会原样显示成「&gt;」。 -->
      <text class="ys-section-head__arrow" decode>{{ '>' }}</text>
    </view>
  </view>
</template>

<script setup>
/**
 * 区块标题（首页「今日可约团课 / 热门课程 / 金牌教练」三处）
 *
 * 实测：标题 16pt 半粗 #2F4754；右侧链接 11pt #627984，箭头是纯文本「>」。
 */
const props = defineProps({
  title: { type: String, required: true },
  moreText: { type: String, default: '' }
})

const emit = defineEmits(['more'])

function onMore() {
  emit('more')
}
</script>

<style lang="scss" scoped>
.ys-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;

  &__title {
    font-size: 31rpx;
    font-weight: 600;
    line-height: 1;
    color: $ys-title;
  }

  &__more {
    display: flex;
    align-items: center;
  }

  &__more-text {
    font-size: 21rpx;
    line-height: 1;
    color: $ys-text-sub;
  }

  &__arrow {
    margin-left: 4rpx;
    font-size: 21rpx;
    line-height: 1;
    color: $ys-text-sub;
  }
}
</style>
