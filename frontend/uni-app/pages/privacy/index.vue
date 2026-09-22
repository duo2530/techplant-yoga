<template>
  <view class="privacy">
    <!-- 正文：15pt / 行高 20pt / 字色 #080808 / 左右内边距 16pt / **段与段之间没有额外间距** -->
    <view v-for="(p, i) in paragraphs" :key="i" class="privacy__p">{{ p }}</view>
  </view>
</template>

<script setup>
/**
 * 隐私政策（pages.json 标题「隐私政策」）
 *
 * 照 docs/原型/privacy.html 还原；原型导航栏左侧那个 #D9D9D9 淡化胶囊是微信原生导航栏的表现，
 * 小程序里由系统绘制，本页不画。
 *
 * 正文实测：15pt / 行高 20pt / 字色 #080808 / 左右内边距 16pt、顶部 6pt / 底部 24pt，
 * 段落之间**没有额外间距**（原型是一串 <p>，浏览器默认 margin 被重置了，所以这里也不加段间距）。
 *
 * 文案**逐字来自 `@/mock/auth.js` 的 privacyContent**（该数组是照 privacy.html 逐字录入的），
 * 本页不自己编内容、不改标点。注意 mock 里第一段开头是「们承诺保障用户…」（疑为原文漏字），
 * 已照原型保留，未自行补字。
 * 与用户协议页结构相同但**不抽公共组件**（约束：不改 components/ 目录），两页各写各的。
 */
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
