<template>
  <view class="fp">
    <!-- 本页原型**没有**「登录/注册」分段控件，白色卡片直接顶到蓝色区域下沿（y=208pt） -->
    <view class="fp__card">
      <!-- 手机号 -->
      <view class="fp__field">
        <image class="fp__icon fp__icon--phone" src="/static/images/auth/icon-phone.png" mode="aspectFit" />
        <input
          v-model="form.phone"
          class="fp__input"
          type="number"
          :maxlength="11"
          placeholder="请输入手机号码"
          placeholder-class="fp__ph"
        />
      </view>

      <!-- 验证码 + 获取验证码 -->
      <view class="fp__field fp__field--sms">
        <image class="fp__icon fp__icon--code" src="/static/images/auth/icon-code.png" mode="aspectFit" />
        <input
          v-model="form.code"
          class="fp__input"
          type="number"
          :maxlength="6"
          placeholder="请输入验证码"
          placeholder-class="fp__ph"
        />
        <view class="fp__sms press" :class="countdown > 0 ? 'fp__sms--disabled' : ''" @click="handleSendCode">
          {{ countdown > 0 ? countdown + 's 后重发' : '获取验证码' }}
        </view>
      </view>

      <!-- 新密码 -->
      <view class="fp__field">
        <image class="fp__icon fp__icon--lock" src="/static/images/auth/icon-lock.png" mode="aspectFit" />
        <input
          v-model="form.password"
          class="fp__input"
          :password="!showPwd"
          placeholder="请输入新密码"
          placeholder-class="fp__ph"
        />
        <image
          class="fp__icon fp__icon--eye press"
          :src="showPwd ? '/static/images/auth/icon-eye-on.png' : '/static/images/auth/icon-eye-off.png'"
          mode="aspectFit"
          @click="showPwd = !showPwd"
        />
      </view>

      <!-- 确认新密码 -->
      <view class="fp__field">
        <image class="fp__icon fp__icon--lock" src="/static/images/auth/icon-lock.png" mode="aspectFit" />
        <input
          v-model="form.confirmPassword"
          class="fp__input"
          :password="!showConfirmPwd"
          placeholder="请再次输入新密码"
          placeholder-class="fp__ph"
        />
        <image
          class="fp__icon fp__icon--eye press"
          :src="showConfirmPwd ? '/static/images/auth/icon-eye-on.png' : '/static/images/auth/icon-eye-off.png'"
          mode="aspectFit"
          @click="showConfirmPwd = !showConfirmPwd"
        />
      </view>

      <!-- 主按钮：原型实测是**左浅右深的蓝色渐变**（不是其他页的纯色 #87C7F6），口径见脚本注释 -->
      <view class="fp__submit press" @click="handleSubmit">修改密码</view>
    </view>
  </view>
</template>

<script setup>
/** 忘记密码 */
import { ref, reactive } from 'vue'
import { onUnload } from '@dcloudio/uni-app'
import { resetPassword, sendSmsCode } from '@/api/auth'

const form = reactive({ phone: '', code: '', password: '', confirmPassword: '' })
const showPwd = ref(false)
const showConfirmPwd = ref(false)
/** 「获取验证码」倒计时秒数（>0 时禁用） */
const countdown = ref(0)
let timer = null
let backTimer = null

onUnload(() => {
  clearInterval(timer)
  clearTimeout(backTimer)
})

/** 发送验证码（真实接口；scene=reset 对应「忘记密码」） */
function handleSendCode() {
  if (countdown.value > 0) {
    return
  }
  sendSmsCode(form.phone, 'reset').then(
    () => {
      uni.showToast({ title: '验证码已发送', icon: 'none' })
      countdown.value = 60
      timer = setInterval(() => {
        countdown.value -= 1
        if (countdown.value <= 0) {
          clearInterval(timer)
          timer = null
        }
      }, 1000)
    },
    () => {
    }
  )
}

/** 提交：两次新密码一致 → resetPassword */
function handleSubmit() {
  if (!form.phone) {
    uni.showToast({ title: '请输入手机号', icon: 'none' })
    return
  }
  if (form.password !== form.confirmPassword) {
    uni.showToast({ title: '两次输入的新密码不一致', icon: 'none' })
    return
  }
  resetPassword({ phone: form.phone, code: form.code, password: form.password }).then(
    (res) => {
      uni.showToast({ title: res.msg || '密码已重置' })
      backTimer = setTimeout(() => {
        uni.navigateBack()
      }, 800)
    },
    () => {
      // 验证码错误／手机号未注册等提示由 @/utils/request 统一 toast
    }
  )
}
</script>

<style lang="scss" scoped>
/* 背景同手机号登录页 */
.fp {
  min-height: 100vh;
  padding-bottom: 60rpx;
  background:
    radial-gradient(150% 80% at 104% -8%, rgba(132, 206, 220, 1) 0%, rgba(132, 206, 220, 0) 62%),
    radial-gradient(140% 190% at 108% 24%, rgba(230, 245, 252, 0.32) 0%, rgba(230, 245, 252, 0) 72%),
    linear-gradient(180deg, #8ac1e6 0%, #8ac1e6 30%, #93c5e3 40%, #c6e2f1 71%, #d8ecf7 85%, #eef5fb 100%);
}

/* 卡片：360pt 宽、上 45 左右 26 下 20pt；卡片顶到蓝色区域下沿（内容区起始 120pt → 231rpx） */
.fp__card {
  width: 692rpx;
  margin: 231rpx auto 0;
  padding: 87rpx 50rpx 38rpx;
  border-radius: 19rpx;
  background: #f8fcfd;
  box-sizing: border-box;
}

.fp__field {
  display: flex;
  align-items: center;
  height: 77rpx;
  padding: 0 33rpx 0 37rpx;
  border: 1px solid #c6d1d8;
  border-radius: 38rpx;
  box-sizing: border-box;
}

.fp__field + .fp__field {
  margin-top: 58rpx;
}

.fp__field--sms {
  padding-right: 19rpx;
}

.fp__icon {
  flex: none;
  margin-right: 19rpx;
}

.fp__icon--phone {
  width: 27rpx;
  height: 37rpx;
}

.fp__icon--code {
  width: 37rpx;
  height: 29rpx;
}

.fp__icon--lock {
  width: 33rpx;
  height: 37rpx;
}

.fp__icon--eye {
  width: 33rpx;
  height: 23rpx;
  margin-right: 0;
}

.fp__input {
  flex: 1;
  min-width: 0;
  height: 77rpx;
  font-size: 27rpx;
  color: #080808;
  background: transparent;
}

.fp__ph {
  color: #c6d1d8;
  font-size: 27rpx;
}

.fp__sms {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 187rpx;
  height: 46rpx;
  border-radius: 23rpx;
  background: linear-gradient(90deg, #76b7eb, #3f81c7);
  color: #ffffff;
  font-size: 21rpx;
}

.fp__sms--disabled {
  opacity: 0.6;
}

/* 主按钮：实测左浅右深的蓝色渐变（非纯色 #87C7F6） */
.fp__submit {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 77rpx;
  margin-top: 75rpx;
  border-radius: 38rpx;
  background: linear-gradient(90deg, #d5edfe 0%, #8cc9f7 100%);
  color: #ffffff;
  font-size: 27rpx;
  letter-spacing: 12rpx;
  text-indent: 12rpx;
}
</style>
