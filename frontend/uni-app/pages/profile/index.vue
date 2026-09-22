<template>
  <!--
    我的（严格照 docs/原型/profile.html）

    状态栏 / 导航栏 / 微信胶囊 / TabBar 由小程序绘制，本页不画。
    导航栏标题「一水瑜伽」已在 pages.json 配好。
    原型导航栏左侧还有一个带斜杠的铃铛（消息入口）——小程序导航栏里放不了，
    改为放在**内容区顶部左侧**（见下面的 .msg-entry），语义与相对位置保持一致。
  -->
  <view class="profile">
    <!-- ===== 消息入口（铃铛）=====
         原型：profile.html 导航栏左侧 `absolute left-2`，36×36pt 可点区，点了跳消息页。
         这里放在系统导航栏正下方、内容区左边缘；图标取 uni-icons 的 notification
         （原型是「带斜杠的铃铛」线稿，uni-icons 没有该字形，取语义最接近的）。 -->
    <view class="msg-entry press" @tap="goMessages">
      <uni-icons type="notification" size="22" color="#333333" />
    </view>
    <!-- ===== 用户信息区 =====
         淡蓝照片背景（mine/header-bg.jpg + 模糊 + 白色蒙版）+ 头像 + 昵称 + 等级徽标 + 手机号 -->
    <view class="hero">
      <view class="hero__bg-box">
        <image class="hero__bg" src="/static/images/mine/header-bg.jpg" mode="aspectFill" />
        <view class="hero__mask" />
      </view>

      <view class="hero__account">
        <image class="hero__avatar" :src="avatarUrl" mode="aspectFill" />
        <view class="hero__meta">
          <view class="hero__row">
            <text class="hero__nickname">{{ profile.nickname || '暂未登录' }}</text>
            <view class="level">
              <text class="level__code">{{ profile.memberLevelCode || 'V0' }}</text>
              <text class="level__name">{{ profile.memberLevel || '普通会员' }}</text>
            </view>
          </view>
          <text class="hero__phone">{{ phoneText }}</text>
        </view>
      </view>
    </view>

    <!-- 占位：把四宫格白卡顶推到原型实测的 y≈188pt（361rpx） -->
    <view class="hero__gap" />

    <!-- ===== 快捷入口（一行四列）：我的会员卡 / 我的优惠券 / 我的礼包 / 我的积分 ===== -->
    <view class="grid">
      <view
        v-for="item in gridItems"
        :key="item.key"
        class="grid__item press"
        @tap="onMenuTap(item)"
      >
        <view class="grid__circle">
          <uni-icons :type="item.icon" size="23" :color="iconColor" />
        </view>
        <text class="grid__label">{{ item.label }}</text>
      </view>
    </view>

    <!-- ===== 分组一：我的合同 / 已约课程 / 我的体测 ===== -->
    <view class="list">
      <view
        v-for="(item, index) in groupOne"
        :key="item.key"
        :class="{ 'list__row--top': index > 0 }"
        @tap="onMenuTap(item)"
      >
        <view v-if="index > 0" class="list__divider" />
        <view class="list__row press">
          <uni-icons class="list__icon" :type="item.icon" size="23" :color="iconColor" />
          <text class="list__label">{{ item.label }}</text>
          <uni-icons class="list__caret" type="right" size="14" color="#BBBBBB" />
        </view>
      </view>
    </view>

    <!-- ===== 分组二：我的体验课 / 用户协议 / 隐私政策 ===== -->
    <view class="list list--last">
      <view
        v-for="(item, index) in groupTwo"
        :key="item.key"
        :class="{ 'list__row--top': index > 0 }"
        @tap="onMenuTap(item)"
      >
        <view v-if="index > 0" class="list__divider" />
        <view class="list__row press">
          <uni-icons class="list__icon" :type="item.icon" size="23" :color="iconColor" />
          <text class="list__label">{{ item.label }}</text>
          <uni-icons class="list__caret" type="right" size="14" color="#BBBBBB" />
        </view>
      </view>
    </view>

    <view class="profile__bottom" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { getUserProfile } from '@/api/member'

/**
 * 我的
 *
 * 数据来自 @/api/member 的 getUserProfile()（内部读 mock），不发任何网络请求。
 * MOCK.loggedIn = false 时返回游客态（暂未登录 / V0 / 普通会员 / 未绑定手机号），
 * 也就是原型截图的状态；= true 时返回 mock 用户。
 *
 * 菜单文案与顺序完全照原型，并**按 mock 的 profileMenus 原顺序**切成三块：
 *   前 4 项 → 一行四列快捷入口；第 5~7 项 → 分组一；第 8~10 项 → 分组二。
 * 这样不新增任何菜单字段，也不重排原型顺序。
 */

/** 菜单图标：原型画的是内联 SVG（深灰线稿）。uni-icons 里没有同款，
 *  按「形状/语义最接近」逐个映射，原型补出图标后以此处为唯一改动点。 */
const MENU_ICONS = {
  cards: 'vip', // 原型：皇冠 —— vip 最接近
  coupons: 'wallet', // 原型：优惠券票券轮廓 —— wallet 最接近
  gifts: 'gift', // 原型：礼盒 —— gift
  points: 'medal', // 原型：钻石 —— medal 最接近
  contracts: 'compose', // 原型：文件 + 文字行 —— compose 最接近
  bookings: 'calendar', // 原型：日历 + 勾 —— calendar
  bodyTests: 'person', // 原型：人体轮廓 + 报告 —— person 最接近
  trials: 'staff', // 原型：举手的人 —— staff 最接近
  terms: 'compose', // 原型：文件 —— compose
  privacy: 'locked' // 原型：盾牌 + 勾 —— locked 最接近
}

/** 原型图标是深灰线稿，不是品牌蓝 */
const iconColor = '#333333'

const profile = ref({})
const menus = ref([])

const avatarUrl = computed(() => profile.value.avatar || '/static/images/mine/avatar.jpg')

/** 未登录（guestInfo.phone 是空串）→ 原型文案「未绑定手机号」 */
const phoneText = computed(() => profile.value.phone || '未绑定手机号')

/** 补上图标，并把 path 归一化（null 表示原型里没有对应页面） */
const menuItems = computed(() =>
  menus.value.map((item) => ({
    key: item.key,
    label: item.label,
    path: item.path || '',
    icon: MENU_ICONS[item.key] || 'right'
  }))
)

const gridItems = computed(() => menuItems.value.slice(0, 4))
const groupOne = computed(() => menuItems.value.slice(4, 7))
const groupTwo = computed(() => menuItems.value.slice(7, 10))

async function loadProfile() {
  const data = await getUserProfile()
  profile.value = (data && data.profile) || {}
  menus.value = (data && data.menus) || []
}

onShow(() => {
  loadProfile()
})

// pages.json 里没配 enablePullDownRefresh，这里只是照着「下拉重新拉数据」的意图保留钩子
onPullDownRefresh(async () => {
  await loadProfile()
  uni.stopPullDownRefresh()
})

/** path 非空按 path 跳；path 为空的菜单项原型里没有页面，只提示 */
function onMenuTap(item) {
  if (!item.path) {
    uni.showToast({ title: '该功能页面暂未提供', icon: 'none' })
    return
  }
  if (item.key === 'bookings') {
    uni.switchTab({ url: item.path })
    return
  }
  uni.navigateTo({ url: item.path })
}

/**
 * 消息入口（原型导航栏左侧那个铃铛）
 *
 * 原型把它画在导航栏里（profile.html 注释：标题「一水瑜伽」左侧为带斜杠的铃铛 = 消息入口）。
 * 小程序导航栏由系统绘制、tabBar 一级页左侧没有可放控件的位置，
 * 所以这里放在**内容区顶部左侧**（系统导航栏正下方），保留原型的相对位置与语义。
 */
function goMessages() {
  uni.navigateTo({ url: '/pages/messages/index' })
}
</script>

<style lang="scss" scoped>
.profile {
  position: relative;
  min-height: 100vh;
  background: $ys-page;
}

/* 消息入口：原型在导航栏左侧（left 8pt、36×36pt 可点区），这里贴在内容区顶部左侧 */
.msg-entry {
  position: absolute;
  left: 8rpx;
  top: 4rpx;
  z-index: 3;
  width: 69rpx;
  height: 69rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ---------------- 用户信息区 ---------------- */
.hero {
  position: relative;
  height: 238rpx;

  /* 背景层：照片 + 模糊（原型 blur(6px)，等价 12rpx）+ 白色蒙版
     原型还带 `-mt-[44px]`，是为了让背景照片的取景窗上移 44pt，
     资产 mine/header-bg.jpg 已经按这个取景窗导出，因此这里不再反向位移。 */
  &__bg-box {
    position: absolute;
    left: 0;
    right: 0;
    top: 0;
    height: 238rpx;
    overflow: hidden;
  }

  &__bg {
    display: block;
    width: 750rpx;
    height: 308rpx;
    filter: blur(12rpx);
  }

  &__mask {
    position: absolute;
    left: 0;
    right: 0;
    top: 0;
    bottom: 0;
    background: linear-gradient(
      180deg,
      rgba(255, 255, 255, 0.2) 0%,
      rgba(255, 255, 255, 0) 45%,
      #ffffff 100%
    );
  }

  /* 账号块：头像圆 52pt（原型 x 40.7..93.4、y 92.3..145.1） */
  &__account {
    position: absolute;
    left: 77rpx;
    top: 121rpx;
    right: 31rpx;
    height: 100rpx;
    display: flex;
    align-items: center;
  }

  /* 圆形头像：外层白环 + 圆形裁切（mode=aspectFill）。
     说明：mine/avatar.jpg 是方形照片，除人物外右侧还有原截图烤进去的「OG」字样，
     aspectFill 会居中裁掉两侧，正好把 O/G 裁在外面；若某些机型裁切不理想，
     改回整张等比显示会露出字样。 */
  &__avatar {
    width: 100rpx;
    height: 100rpx;
    flex: none;
    border-radius: 50%;
    border: 4rpx solid rgba(255, 255, 255, 0.85);
    box-sizing: border-box;
    background: #fff;
  }

  &__meta {
    margin-left: 25rpx;
    flex: 1;
    min-width: 0;
  }

  &__row {
    display: flex;
    align-items: center;
  }

  &__nickname {
    font-size: 37rpx;
    font-weight: 600;
    line-height: 1;
    color: $ys-ink-1;
  }

  &__phone {
    display: block;
    margin-top: 10rpx;
    font-size: 23rpx;
    line-height: 1;
    color: $ys-ink-2;
  }

  /* 间距块：把四宫格卡顶推到原型实测的 y=188pt（361rpx） */
  &__gap {
    height: 123rpx;
  }
}

/* 会员等级徽标：V0 段 #4A4A4A + 等级名段 #5C5C5C，整块高 19pt 胶囊 */
.level {
  display: flex;
  align-items: center;
  height: 37rpx;
  margin-left: 15rpx;
  border-radius: 37rpx;
  overflow: hidden;
  flex: none;

  &__code {
    padding: 0 12rpx;
    font-size: 19rpx;
    line-height: 37rpx;
    color: rgba(255, 255, 255, 0.95);
    background: #4a4a4a;
  }

  &__name {
    padding: 0 13rpx;
    font-size: 19rpx;
    line-height: 37rpx;
    color: #fff;
    background: #5c5c5c;
  }
}

/* ---------------- 快捷入口（一行四列） ---------------- */
.grid {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  height: 187rpx;
  margin: 0 31rpx;
  padding-top: 46rpx;
  background: #fff;
  border-radius: $ys-radius-lg;
  box-shadow: $ys-card-shadow;

  &__item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  &__circle {
    width: 88rpx;
    height: 88rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: $ys-brand-soft;
  }

  &__label {
    margin-top: 13rpx;
    font-size: 21rpx;
    line-height: 1;
    color: $ys-ink-1;
  }
}

/* ---------------- 菜单分组 ---------------- */
.list {
  margin: 48rpx 31rpx 0;
  background: #fff;
  border-radius: $ys-radius-lg;
  box-shadow: $ys-card-shadow;
  overflow: hidden;

  &--last {
    margin-bottom: 0;
  }

  &__row {
    display: flex;
    align-items: center;
    box-sizing: border-box;
    height: 102rpx;
    padding: 0 31rpx;

    /* 分组内第 2、3 行：上方压一条分隔线（线本身不带高度，行高不变） */
    &--top {
      border-top: 1px solid $ys-line;
    }
  }

  &__icon {
    width: 50rpx;
    flex: none;
    text-align: center;
  }

  &__label {
    flex: 1;
    margin-left: 25rpx;
    font-size: 29rpx;
    line-height: 1;
    color: $ys-ink-1;
  }

  &__caret {
    flex: none;
  }

  /* 分隔线左边距照原型 ml-4（16pt） */
  &__divider {
    height: 1px;
    margin-left: 31rpx;
    background: $ys-line;
  }
}

.profile__bottom {
  height: 27rpx;
}
</style>
