<template>
  <view class="ql">
    <!-- 品牌区：Logo 人形 + 书法品牌字是一张合成 Logo 图（原型里就是整块雪碧图切出来的），
         原型实测 303.3×170pt → 583×327rpx -->
    <image class="ql__brand" src="/static/images/auth/logo-block.png" mode="widthFix" />

    <!-- Slogan：实测 #AAAAAA / 15pt -->
    <view class="ql__slogan">练瑜伽普拉提就来一水</view>

    <!-- 主按钮：实测 312×46pt、圆角 20pt、#87C7F6、白字 17pt -->
    <view class="ql__btn press" @click="handleWechatLogin">快速登录</view>

    <!-- 「输入手机号登录/注册」→ 手机号登录/注册页 -->
    <view class="ql__link press" @click="goPhoneAuth">输入手机号登录/注册</view>

    <!-- 协议行：未勾选圆框 + 协议文案（原型整行同为 #080808，两个协议名没有蓝色高亮） -->
    <view class="ql__agree">
      <image
        class="ql__agree-box press"
        :src="agreed ? '/static/images/auth/checkbox-on.png' : '/static/images/auth/checkbox-off.png'"
        mode="aspectFit"
        @click="agreed = !agreed"
      />
      <view class="ql__agree-text">
        <text>已阅读并同意“</text>
        <!-- #ifdef H5 -->
        <a class="ql__agree-name" href="/pages/terms/index">用户协议</a>
        <!-- #endif -->
        <!-- #ifndef H5 -->
        <text class="ql__agree-name press" @click="goDoc('/pages/terms/index')">用户协议</text>
        <!-- #endif -->
        <text>”和“</text>
        <!-- #ifdef H5 -->
        <a class="ql__agree-name" href="/pages/privacy/index">隐私政策</a>
        <!-- #endif -->
        <!-- #ifndef H5 -->
        <text class="ql__agree-name press" @click="goDoc('/pages/privacy/index')">隐私政策</text>
        <!-- #endif -->
        <text>”各条款</text>
      </view>
    </view>
  </view>
</template>

<script setup>
/** 快速登录页 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { loginByWechat } from '@/api/auth'

/** 是否已勾选协议（原型为未勾选态） */
const agreed = ref(false)

onLoad((options) => {
  // 从「我的」等页面跳来且未登录时带过来的提示（有就显示，无则不打扰）
  if (options && options.redirectMsg) {
    uni.showToast({ title: options.redirectMsg, icon: 'none' })
  }
})

/** 微信快捷登录（mock） */
function handleWechatLogin() {
  if (!agreed.value) {
    uni.showToast({ title: '请先阅读并同意协议', icon: 'none' })
    return
  }
  loginByWechat().then((res) => {
    if (res.success) {
      uni.showToast({ title: '登录成功' })
      uni.switchTab({ url: '/pages/home/index' })
    } else {
      uni.showToast({ title: res.message || '登录失败', icon: 'none' })
    }
  })
}

/** 去手机号登录/注册 */
function goPhoneAuth() {
  uni.navigateTo({ url: '/pages/auth/phone-auth' })
}

/** 看协议（用户协议 / 隐私政策） */
function goDoc(path) {
  uni.navigateTo({ url: path })
}
</script>

<style lang="scss" scoped>
/* 白底整页；不使用 .ys-page（它是灰底） */
page {
  background: #ffffff;
}

.ql {
  min-height: 100vh;
  padding-top: 100rpx; /* 原型内容区起始到品牌区 52pt */
  background: #ffffff;
}

/* Logo 区块 303.3×170pt → 583×327rpx，水平居中 */
.ql__brand {
  display: block;
  width: 583rpx;
  height: 327rpx;
  margin: 0 auto;
}

/* Slogan：原型 15pt/#AAAAAA，品牌区到 Slogan 间距 24pt */
.ql__slogan {
  margin-top: 46rpx;
  font-size: 29rpx;
  line-height: 29rpx;
  color: $ys-ink-muted;
  text-align: center;
}

/* 主按钮：312×46pt、圆角 20pt、#87C7F6、白字 17pt；Slogan 到按钮 30pt */
.ql__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 600rpx;
  height: 88rpx;
  margin: 58rpx auto 0;
  border-radius: 38rpx;
  background: $ys-brand-light;
  color: #ffffff;
  font-size: 33rpx;
}

/* 「输入手机号登录/注册」：14pt/#AAAAAA；按钮到该行 19pt */
.ql__link {
  margin-top: 37rpx;
  font-size: 27rpx;
  line-height: 27rpx;
  color: $ys-ink-muted;
  text-align: center;
}

/* 协议行：该行到按钮区 81pt；文字 14pt/行高 17pt/#080808 */
.ql__agree {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  margin-top: 156rpx;
  padding: 0 26rpx;
}

/* 未勾选圆框 17pt → 33rpx，1px #AAAAAA 圆环 */
.ql__agree-box {
  flex: none;
  width: 33rpx;
  height: 33rpx;
  margin-top: 3rpx; /* 与首行文字视觉对齐 */
  margin-right: 12rpx;
}

.ql__agree-text {
  font-size: 27rpx;
  line-height: 33rpx;
  color: #080808;
}

/* 协议名与整行同色（原型无蓝色高亮），仅可点 */
.ql__agree-name {
  color: #080808;
  text-decoration: none;
}
</style>
