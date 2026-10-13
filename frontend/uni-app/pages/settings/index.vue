<template>
  <view class="st">
    <!-- 顶部 4pt 间隙（导航栏与第一张卡片之间的灰色带） -->
    <view class="st__gap st__gap--top" />

    <!-- ===== 卡片一：账号资料（用户头像 / 用户名 / 用户角色，全宽白底） ===== -->
    <view class="st__card">
      <view class="st__row st__row--avatar press" @tap="onAvatarTap">
        <view class="st__text">
          <text class="st__title">用户头像</text>
          <text class="st__sub">有趣的头像，百里挑一</text>
        </view>
        <image class="st__avatar" :src="avatarUrl" mode="aspectFill" />
      </view>
      <view class="st__row">
        <text class="st__title">用户名</text>
        <text class="st__value">{{ nickname }}</text>
      </view>
      <view class="st__row">
        <text class="st__title">用户角色</text>
      </view>
    </view>

    <!-- 40pt 灰色带：账号资料与下面各功能项之间（原型实测这一段明显更宽） -->
    <view class="st__gap st__gap--wide" />

    <!-- ===== 账号安全 ===== -->
    <view class="st__card">
      <view class="st__row st__row--two press" @tap="onUnavailable">
        <view class="st__text">
          <text class="st__title">账号安全</text>
          <text class="st__sub">修改用户密码</text>
        </view>
        <uni-icons class="st__caret" type="right" size="14" :color="caretColor" />
      </view>
    </view>

    <view class="st__gap" />

    <!-- ===== 注销账户 ===== -->
    <view class="st__card">
      <view class="st__row press" @tap="onUnavailable">
        <text class="st__title">注销账户</text>
        <uni-icons class="st__caret" type="right" size="14" :color="caretColor" />
      </view>
    </view>

    <view class="st__gap" />

    <!-- ===== 反馈 ===== -->
    <view class="st__card">
      <view class="st__row press" @tap="onUnavailable">
        <text class="st__title">反馈</text>
        <uni-icons class="st__caret" type="right" size="14" :color="caretColor" />
      </view>
    </view>

    <view class="st__gap" />

    <!-- ===== 提交日志 ===== -->
    <view class="st__card">
      <view class="st__row st__row--two press" @tap="onUnavailable">
        <view class="st__text">
          <text class="st__title">提交日志</text>
          <text class="st__sub">收集运行日志，便于排查问题</text>
        </view>
        <uni-icons class="st__caret" type="right" size="14" :color="caretColor" />
      </view>
    </view>

    <view class="st__gap" />

    <!-- ===== 用户协议 / 隐私协议（同卡片，中间一条分隔线） ===== -->
    <view class="st__card">
      <view class="st__row press" @tap="goDoc('/pages/terms/index')">
        <text class="st__title">用户协议</text>
        <uni-icons class="st__caret" type="right" size="14" :color="caretColor" />
      </view>
      <view class="st__row press" @tap="goDoc('/pages/privacy/index')">
        <text class="st__title">隐私协议</text>
        <uni-icons class="st__caret" type="right" size="14" :color="caretColor" />
      </view>
    </view>

    <view class="st__gap" />

    <!-- ===== 应用版本（标题 + 版本号，无箭头） ===== -->
    <view class="st__card st__card--last">
      <view class="st__row st__row--two">
        <view class="st__text">
          <text class="st__title">应用版本</text>
          <text class="st__sub">{{ appVersion }}</text>
        </view>
      </view>
    </view>

    <!-- ===== 退出登录（固定悬浮在主内容之上，原型里就压住了「应用版本」行） ===== -->
    <view class="st__logout press" @tap="handleLogout">退出登录</view>
  </view>
</template>

<script setup>
/** 设置页（照 docs/产品分析/用户端小程序截图/P4-我的-02-设置.png 复刻） */
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import config from '@/config'
import { getUserProfile, logout } from '@/api/member'
import { LOGIN_PAGE } from '@/utils/session'
import { useUserStore } from '@/store'

/** 右侧值／箭头色（原型实测 #78909C，与登录页图标同色） */
const caretColor = '#78909C'

const profile = ref({})

const avatarUrl = computed(() => profile.value.avatar || '/static/images/mine/avatar.jpg')
const nickname = computed(() => profile.value.nickname || '暂未登录')
const appVersion = computed(() => (config.appInfo && config.appInfo.version) || '')

onShow(() => {
  loadProfile()
})

/** 与「我的」页同源：有会员 token 时是真实会员显示名，否则是游客态 */
async function loadProfile() {
  const data = await getUserProfile()
  profile.value = (data && data.profile) || {}
}

/** 头像行：换头像功能未交付 */
function onAvatarTap() {
  uni.showToast({ title: '该功能页面暂未提供', icon: 'none' })
}

/** 没有对应页面的功能项 */
function onUnavailable() {
  uni.showToast({ title: '该功能页面暂未提供', icon: 'none' })
}

function goDoc(path) {
  uni.navigateTo({ url: path })
}

/** 退出登录：先尽力撤销服务端会话，再清本地登录态并回登录页 */
function handleLogout() {
  uni.showModal({
    title: '提示',
    content: '确定要退出登录吗？',
    success: (res) => {
      if (!res.confirm) {
        return
      }
      logout()
        .catch(() => {
          // 网络异常也要能退出：本地登录态照样清掉（服务端会话到期后自然失效）
        })
        .then(() => {
          finishLogout()
        })
    }
  })
}

/** 清本地登录态（存储 + user store）并回登录页 —— 走统一方法，与 401、会话过期同一套清理 */
function finishLogout() {
  useUserStore().clearSession()
  uni.showToast({ title: '已退出登录' })
  uni.reLaunch({ url: LOGIN_PAGE })
}
</script>

<style lang="scss" scoped>
/* 页面底 = 卡片之间的灰带色（实测 #F5F5F5） */
.st {
  min-height: 100vh;
  background: #f5f5f5;
}

/* 卡片之间的灰带：10pt → 19rpx */
.st__gap {
  height: 19rpx;

  &--top {
    height: 8rpx; /* 导航栏与第一张卡片之间 4pt */
  }

  &--wide {
    height: 77rpx; /* 账号资料 → 账号安全：40pt */
  }
}

/* 全宽白卡片：无圆角、无投影（与「我的」页的圆角卡片不是一套） */
.st__card {
  background: #ffffff;

  &--last {
    /* 版本号下方留白 29pt，页面到底 */
    padding-bottom: 56rpx;
  }
}

/* 行：单行 58.3pt → 112rpx；标题+副标题 78.3pt → 150rpx；头像行 70pt → 135rpx */
.st__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
  height: 112rpx;
  padding: 0 30rpx;

  /* 用户头像行：70pt → 135rpx（头像比文字块高，行高单独给） */
  &--avatar {
    height: 135rpx;
  }

  /* 标题+副标题行：78.3pt → 150rpx；高度按内容撑开，避免字体放大时裁字 */
  &--two {
    height: auto;
    min-height: 150rpx;
  }
}

/* 卡片内第 2 行起：上方压一条全宽发丝线（实测 (248,249,251)，比 token $ys-line 更浅） */
.st__row + .st__row {
  border-top: 1px solid #f8f9fb;
}

/* 标题 16pt → 31rpx，#080808；副标题 13pt → 25rpx，#AAAAAA，间距 13.4pt → 26rpx */
.st__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.st__title {
  font-size: 31rpx;
  line-height: 1;
  color: #080808;
}

.st__sub {
  margin-top: 26rpx;
  font-size: 25rpx;
  line-height: 1;
  color: $ys-ink-muted;
}

/* 右侧值：与箭头同色（实测 #78909C） */
.st__value {
  flex: none;
  margin-left: 19rpx;
  font-size: 31rpx;
  line-height: 1;
  color: #78909c;
}

.st__caret {
  flex: none;
  margin-left: 19rpx;
}

/* 头像行右侧：40pt 圆形头像 */
.st__avatar {
  flex: none;
  width: 77rpx;
  height: 77rpx;
  border-radius: 50%;
  background: #f5f5f5;
}

/* 退出登录：固定悬浮，222×40pt → 427×77rpx，圆角 20pt → 38rpx，距屏底 59pt → 114rpx */
.st__logout {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 114rpx;
  z-index: 5;
  width: 427rpx;
  height: 77rpx;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 38rpx;
  background: $ys-brand-light;
  color: #ffffff;
  font-size: 27rpx;
  /* 原型按钮外有一圈淡蓝发光 */
  box-shadow: 0 0 24rpx rgba(135, 199, 246, 0.55);
}
</style>
