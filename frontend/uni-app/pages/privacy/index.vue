<template>
  <view class="privacy">
    <!-- 正文：15pt / 行高 20pt / 字色 #080808 / 左右内边距 16pt / **段与段之间没有额外间距** -->
    <view v-for="(p, i) in paragraphs" :key="i" class="privacy__p">{{ p }}</view>
  </view>
</template>

<script setup>
/** 隐私政策 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getAgreementContent } from '@/api/auth'

/** 政策正文段落数组 */
const paragraphs = ref([])

onLoad(() => {
  getAgreementContent('privacy').then((list) => {
    paragraphs.value = list || []
  })
})
</script>

<style lang="scss" scoped>
/* 政策页是白底（不是全局的 #F7F9FC 灰底），所以不用 .ys-page，自己铺一层白的 */
.privacy {
  min-height: 100vh;
  padding: 12rpx 31rpx 46rpx;
  background: #ffffff;
  box-sizing: border-box;
}

.privacy__p {
  margin: 0; /* 段与段之间不加间距（照原型） */
  font-size: 29rpx;
  line-height: 38rpx;
  color: #080808;
  word-break: break-all;
}
</style>
