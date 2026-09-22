<template>
  <view class="pa">
    <!-- 分段控件（登录 / 注册）：可点击切换，默认登录。原型 246.7×40pt、圆角 20pt、底色 #F2F9FC -->
    <view class="pa__seg">
      <view
        class="pa__seg-btn press"
        :class="mode === 'login' ? 'pa__seg-btn--on-login' : ''"
        @click="mode = 'login'"
        >登 录</view
      >
      <view
        class="pa__seg-btn press"
        :class="mode === 'register' ? 'pa__seg-btn--on-register' : ''"
        @click="mode = 'register'"
        >注 册</view
      >
    </view>

    <!-- ========== 登录态：手机号 + 密码 + 登录按钮 + 快捷登录/忘记密码 ========== -->
    <view v-if="mode === 'login'" class="pa__card">
      <view class="pa__field">
        <image class="pa__icon pa__icon--phone" src="/static/images/auth/icon-phone.png" mode="aspectFit" />
        <input
          v-model="loginForm.phone"
          class="pa__input"
          type="number"
          :maxlength="11"
          placeholder="请输入登录手机号码"
          placeholder-class="pa__ph"
        />
      </view>

      <view class="pa__field">
        <image class="pa__icon pa__icon--lock" src="/static/images/auth/icon-lock.png" mode="aspectFit" />
        <input
          v-model="loginForm.password"
          class="pa__input"
          :password="!showLoginPwd"
          placeholder="请输入登录密码"
          placeholder-class="pa__ph"
        />
        <image
          class="pa__icon pa__icon--eye press"
          :src="showLoginPwd ? '/static/images/auth/icon-eye-on.png' : '/static/images/auth/icon-eye-off.png'"
          mode="aspectFit"
          @click="showLoginPwd = !showLoginPwd"
        />
      </view>

      <view class="pa__submit press" @click="handleLogin">登 录</view>

      <view class="pa__links">
        <text class="pa__link-quick press" @click="goQuickLogin">快捷登录</text>
        <text class="pa__link-forgot press" @click="goForgot">忘记密码？</text>
      </view>
    </view>

    <!-- ========== 注册态：手机号 + 验证码 + 密码 + 注册按钮（无底部链接行） ========== -->
    <view v-else class="pa__card">
      <view class="pa__field">
        <image class="pa__icon pa__icon--phone" src="/static/images/auth/icon-phone.png" mode="aspectFit" />
        <input
          v-model="regForm.phone"
          class="pa__input"
          type="number"
          :maxlength="11"
          placeholder="请输入注册手机号码"
          placeholder-class="pa__ph"
        />
      </view>

      <view class="pa__field pa__field--sms">
        <image class="pa__icon pa__icon--code" src="/static/images/auth/icon-code.png" mode="aspectFit" />
        <input
          v-model="regForm.code"
          class="pa__input"
          type="number"
          :maxlength="6"
          placeholder="请输入验证码"
          placeholder-class="pa__ph"
        />
        <view class="pa__sms press" :class="countdown > 0 ? 'pa__sms--disabled' : ''" @click="handleSendCode(regForm.phone)">
          {{ countdown > 0 ? countdown + 's 后重发' : '获取验证码' }}
        </view>
      </view>

      <view class="pa__field">
        <image class="pa__icon pa__icon--lock" src="/static/images/auth/icon-lock.png" mode="aspectFit" />
        <input
          v-model="regForm.password"
          class="pa__input"
          :password="!showRegPwd"
          placeholder="请输入登录密码"
          placeholder-class="pa__ph"
        />
        <image
          class="pa__icon pa__icon--eye press"
          :src="showRegPwd ? '/static/images/auth/icon-eye-on.png' : '/static/images/auth/icon-eye-off.png'"
          mode="aspectFit"
          @click="showRegPwd = !showRegPwd"
        />
      </view>

      <view class="pa__submit press" @click="handleRegister">注 册</view>
    </view>

    <!-- 协议行（两种状态共用，都在卡片下方）：未勾选圆框 + 文案 -->
    <view class="pa__agree">
      <image
        class="pa__agree-box press"
        :src="agreed ? '/static/images/auth/checkbox-on.png' : '/static/images/auth/checkbox-off.png'"
        mode="aspectFit"
        @click="agreed = !agreed"
      />
      <view class="pa__agree-text">
        <text>已阅读并同意“</text>
        <!-- #ifdef H5 -->
        <a class="pa__agree-name" href="/pages/terms/index">用户协议</a>
        <!-- #endif -->
        <!-- #ifndef H5 -->
        <text class="pa__agree-name press" @click="goDoc('/pages/terms/index')">用户协议</text>
        <!-- #endif -->
        <text>”和“</text>
        <!-- #ifdef H5 -->
        <a class="pa__agree-name" href="/pages/privacy/index">隐私政策</a>
        <!-- #endif -->
        <!-- #ifndef H5 -->
        <text class="pa__agree-name press" @click="goDoc('/pages/privacy/index')">隐私政策</text>
        <!-- #endif -->
        <text>”各条款</text>
      </view>
    </view>
  </view>
</template>

<script setup>
/**
 * 手机号登录 / 注册（pages.json 标题「手机号登录」；登录、注册是同一页的两个 Tab，可点击切换）
 *
 * 照 docs/原型/phone-auth.html 还原；导航栏灰色胶囊、微信胶囊、状态栏都是原型整机复刻的取巧，
 * 小程序里由系统绘制，本页不画。
 *
 * 【原型实测关键值（源图 1170×2532 @3x → 逻辑 390×844）】
 *  - 背景：上部蓝（#8AC1E6，右上偏青 #85CEDC）→ 下部极淡蓝白（#C6E2F1 → #EEF5FB）；
 *  - 分段控件 246.7×40pt、圆角 20pt，选中半边 linear-gradient(90deg,#CDE8FD,#9DD1F8)，
 *    未选中半边 #F2F9FC，文字 15pt、字距 6pt、文案「登 录」「注 册」；
 *  - 卡片 360pt 宽（左右各 15pt）、圆角 10pt、底色 #F8FCFD、内边距 上 45 / 左右 26 / 下 20pt；
 *  - 输入框 308×40pt、圆角 20pt、1px #C6D1D8 边框、左图标 #78909C、占位符 14pt #C6D1D8，框间距 30.3pt；
 *  - 主按钮 308×40pt、圆角 20pt、#87C7F6、白字 14pt、字距 6pt；输入框→按钮 39pt；
 *  - 登录态多一行：左「快捷登录」(#87C7F6) + 右「忘记密码？」(#AAAAAA)，14pt，两端对齐，按钮→该行 37pt；
 *    注册态没有这一行；
 *  - 注册态第 2 个输入框右侧「获取验证码」小按钮 97.3×23.7pt、圆角 12pt、
 *    渐变 linear-gradient(90deg,#76B7EB,#3F81C7)、白字 11pt；
 *  - 卡片下方协议行：未勾选圆框 17pt（1px #AAAAAA）+ 文案 14pt/#080808，卡片底→该行 65pt。
 *
 * 【与原型的有意出入 / 没把握的点】
 *  1. **没有使用全局 `.ys-field`**：那套表单行是「96rpx 高 + 下划线 + 左侧 label + 红色星号」，
 *     而本页原型是**圆角胶囊输入框 + 左侧线性图标 + 无 label**（308×40pt、1px 边框、无下划线）。
 *     规范 §1「复刻不是重设计、截图上没有的不许发明」优先，所以按原型做胶囊框，未加 label 与星号。
 *     （任务书里提到「用 .ys-field 表单行与 $ys-star 必填星号」——本页原型里没有这两种元素，
 *      按视觉权威（原型）处理；若要改成 .ys-field 风格，需要评审确认。）
 *  2. **两张截图的「选中 Tab 文字颜色」互相矛盾**（原型已像素确认）：手机登录页.png 选中的「登 录」
 *     是深灰蓝 #78909C；手机注册页.png 选中的「注 册」是纯白 #FFFFFF。这里按原型分别还原
 *     （见 .pa__seg-btn--on-login / --on-register），所以切换 Tab 时选中文字颜色会变。
 *  3. 输入框左侧图标原型是内联 SVG（有 2.5pt 圆角、`</>` 等细节，小程序不支持内联 SVG），
 *     这里用等尺寸 PNG（同色 #78909C）替代，见 static/images/auth/icon-*.png。
 *  4. 密码框右侧「闭眼」图标原型不可点击（无展开态）；这里做成了可点击切换明文，
 *     睁眼图标是按闭眼同源补画的（颜色/线宽一致，字形未经原型确认）。
 *  5. 「获取验证码」加了 **60 秒倒计时**：倒计时内按钮显示「Ns 后重发」并禁止再次点击
 *     （原型没有该状态，属交互补充；改文案/去掉只动 .pa__sms 一处）。
 *  6. mock 验证码固定 8888，`sendSmsCode()` 返回的 message 会带出来，用 toast 提示。
 */
import { ref, reactive } from 'vue'
import { onUnload } from '@dcloudio/uni-app'
import { loginByPhone, register, sendSmsCode } from '@/api/auth'

/** 当前 Tab：login / register */
const mode = ref('login')
/** 协议勾选（原型为未勾选态） */
const agreed = ref(false)
/** 密码明文开关（原型只有闭眼图标） */
const showLoginPwd = ref(false)
const showRegPwd = ref(false)
/** 「获取验证码」倒计时秒数（>0 时禁用） */
const countdown = ref(0)
let timer = null

const loginForm = reactive({ phone: '', password: '' })
const regForm = reactive({ phone: '', code: '', password: '' })

onUnload(() => {
  clearInterval(timer)
})

/** 校验协议勾选 */
function checkAgreed() {
  if (!agreed.value) {
    uni.showToast({ title: '请先阅读并同意协议', icon: 'none' })
    return false
  }
  return true
}

/** 发送验证码（mock：固定 8888，message 里会带出来） */
function handleSendCode(phone) {
  if (countdown.value > 0) {
    return
  }
  sendSmsCode(phone).then((res) => {
    uni.showToast({ title: res.message, icon: 'none' })
    if (res.success) {
      countdown.value = 60
      timer = setInterval(() => {
        countdown.value -= 1
        if (countdown.value <= 0) {
          clearInterval(timer)
          timer = null
        }
      }, 1000)
    }
  })
}

/** 登录 */
function handleLogin() {
  if (!checkAgreed()) {
    return
  }
  loginByPhone({ phone: loginForm.phone, password: loginForm.password }).then((res) => {
    if (res.success) {
      uni.showToast({ title: '登录成功' })
      uni.switchTab({ url: '/pages/home/index' })
    } else {
      uni.showToast({ title: res.message || '登录失败', icon: 'none' })
    }
  })
}

/** 注册 */
function handleRegister() {
  if (!checkAgreed()) {
    return
  }
  register({ phone: regForm.phone, code: regForm.code, password: regForm.password }).then((res) => {
    if (res.success) {
      uni.showToast({ title: '注册成功' })
      uni.switchTab({ url: '/pages/home/index' })
    } else {
      uni.showToast({ title: res.message || '注册失败', icon: 'none' })
    }
  })
}

function goQuickLogin() {
  uni.navigateBack()
}

function goForgot() {
  uni.navigateTo({ url: '/pages/auth/forgot-password' })
}

function goDoc(path) {
  uni.navigateTo({ url: path })
}
</script>

<style lang="scss" scoped>
/* 原型整页渐变：右上偏青的径向高光 + 竖直蓝→淡蓝白渐变 */
.pa {
  min-height: 100vh;
  padding-bottom: 60rpx;
  background:
    radial-gradient(150% 80% at 104% -8%, rgba(132, 206, 220, 1) 0%, rgba(132, 206, 220, 0) 62%),
    radial-gradient(140% 190% at 108% 24%, rgba(230, 245, 252, 0.32) 0%, rgba(230, 245, 252, 0) 72%),
    linear-gradient(180deg, #8ac1e6 0%, #8ac1e6 30%, #93c5e3 40%, #c6e2f1 71%, #d8ecf7 85%, #eef5fb 100%);
}

/* 分段控件：246.7×40pt → 474×77rpx，圆角 38rpx */
.pa__seg {
  display: flex;
  width: 474rpx;
  height: 77rpx;
  margin: 231rpx auto 0;
  border-radius: 38rpx;
  background: #f2f9fc;
  overflow: hidden;
}

.pa__seg-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 29rpx;
  letter-spacing: 12rpx;
  text-indent: 12rpx; /* 抵掉最后一个字后面的字距，保证视觉居中 */
  color: #78909c;
}

.pa__seg-btn--on-login {
  background: linear-gradient(90deg, #cde8fd, #9dd1f8);
  color: #78909c; /* 手机登录页.png 实测选中色 */
}

.pa__seg-btn--on-register {
  background: linear-gradient(90deg, #cde8fd, #9dd1f8);
  color: #ffffff; /* 手机注册页.png 实测选中色（与上面故意不一致，见脚本注释 2） */
}

/* 卡片：360pt 宽、圆角 10pt、#F8FCFD、上 45 左右 26 下 20pt；分段控件→卡片 54pt */
.pa__card {
  width: 692rpx;
  margin: 104rpx auto 0;
  padding: 87rpx 50rpx 38rpx;
  border-radius: 19rpx;
  background: #f8fcfd;
  box-sizing: border-box;
}

/* 输入框：308×40pt → 592×77rpx，胶囊、1px #C6D1D8 边框 */
.pa__field {
  display: flex;
  align-items: center;
  height: 77rpx;
  padding: 0 33rpx 0 37rpx;
  border: 1px solid #c6d1d8;
  border-radius: 38rpx;
  box-sizing: border-box;
}

.pa__field + .pa__field {
  margin-top: 58rpx;
}

/* 验证码行：右侧要给「获取验证码」按钮留位（原型 padding-right 10pt） */
.pa__field--sms {
  padding-right: 19rpx;
}

.pa__icon {
  flex: none;
  margin-right: 19rpx;
}

/* 图标尺寸按原型内联 SVG 的逻辑尺寸（手机 14×19 / 验证码 19×15 / 挂锁 17×19 / 闭眼 17×12 pt） */
.pa__icon--phone {
  width: 27rpx;
  height: 37rpx;
}

.pa__icon--code {
  width: 37rpx;
  height: 29rpx;
}

.pa__icon--lock {
  width: 33rpx;
  height: 37rpx;
}

.pa__icon--eye {
  width: 33rpx;
  height: 23rpx;
  margin-right: 0;
}

/* 输入文字 14pt #080808，占位符 14pt #C6D1D8 */
.pa__input {
  flex: 1;
  min-width: 0;
  height: 77rpx;
  font-size: 27rpx;
  color: #080808;
  background: transparent;
}

.pa__ph {
  color: #c6d1d8;
  font-size: 27rpx;
}

/* 「获取验证码」：97.3×23.7pt → 187×46rpx，圆角 23rpx，蓝色渐变，白字 11pt */
.pa__sms {
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

.pa__sms--disabled {
  opacity: 0.6;
}

/* 主按钮：308×40pt → 592×77rpx、圆角 38rpx、#87C7F6、白字 14pt、字距 6pt；距上方输入框 39pt */
.pa__submit {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 77rpx;
  margin-top: 75rpx;
  border-radius: 38rpx;
  background: $ys-brand-light;
  color: #ffffff;
  font-size: 27rpx;
  letter-spacing: 12rpx;
  text-indent: 12rpx;
}

/* 登录态底部链接行：按钮→该行 37pt，两端对齐 */
.pa__links {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 71rpx;
  font-size: 27rpx;
  line-height: 27rpx;
}

.pa__link-quick {
  color: $ys-brand-light;
}

.pa__link-forgot {
  color: $ys-ink-muted;
}

/* 协议行：卡片底→该行 65pt，文字 14pt/#080808 */
.pa__agree {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  margin-top: 125rpx;
  padding: 0 26rpx;
}

.pa__agree-box {
  flex: none;
  width: 33rpx;
  height: 33rpx;
  margin-top: 3rpx;
  margin-right: 12rpx;
}

.pa__agree-text {
  font-size: 27rpx;
  line-height: 33rpx;
  color: #080808;
}

.pa__agree-name {
  color: #080808;
  text-decoration: none;
}
</style>
