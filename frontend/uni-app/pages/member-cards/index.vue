<template>
  <!--
    会员卡（我的会员卡）

    参照真机截图 docs/产品分析/用户端小程序截图/P4-我的-04-会员卡-*.jpg：
    顶部一段浅蓝装饰底、卡片列表居中、底部「已经到底了」收尾。
    卡片版式在 ys-member-card 组件里，本页负责分页加载、登录态续期刷新，以及空态／加载态／失败重试／到底文案。
  -->
  <view class="cards">
    <view class="cards__bg" />

    <view v-if="cards.length && !needLogin" class="cards__list">
      <ys-member-card v-for="item in cards" :key="item.id" :card="item" />
    </view>

    <!-- 未登录、查询失败与「确实没有卡」是三种状态：只有成功且结果为空才显示无卡 -->
    <view v-if="needLogin" class="cards__error">
      <text class="cards__error-text">登录后查看我的会员卡</text>
      <view class="cards__retry press" @tap="goLogin">
        <text class="cards__retry-text">去登录</text>
      </view>
    </view>
    <view v-else-if="error && !cards.length" class="cards__error">
      <text class="cards__error-text">会员卡加载失败，请稍后重试</text>
      <view class="cards__retry press" @tap="reload">
        <text class="cards__retry-text">重新加载</text>
      </view>
    </view>
    <view v-else-if="!cards.length && !loading" class="cards__empty">
      <ys-empty title="暂无会员卡" :padding-top="160" />
    </view>

    <view v-if="loading" class="cards__state">
      <text class="cards__state-text">加载中...</text>
    </view>
    <view v-else-if="error && cards.length && !needLogin" class="cards__foot cards__foot--retry" @tap="reload">
      <text class="cards__foot-text">加载失败，点击重试</text>
    </view>
    <view v-else-if="cards.length && !hasMore && !needLogin" class="cards__foot">
      <view class="cards__foot-line" />
      <text class="cards__foot-text">已经到底了</text>
      <view class="cards__foot-line" />
    </view>

    <view class="cards__bottom" />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { listMemberCards } from '@/api/member'
import { isSessionValid, LOGIN_PAGE } from '@/utils/session'

/** 我的会员卡：接口见《用户端接口详细设计》查询我的会员卡 */

const PAGE_SIZE = 10

const cards = ref([])
const pageNum = ref(0)
const total = ref(0)
const loading = ref(false)
const error = ref(false)
const needLogin = ref(false)

/** 后端已按 id 倒序返回，翻页只按已加载条数与 total 比较 */
const hasMore = computed(() => cards.value.length < total.value)

/** 未登录只跳一次登录页，避免 onShow 反复入栈 */
let loginRedirected = false

/** onLoad 已经拉过首屏，紧接的第一次 onShow 不再重复请求 */
let firstShow = true

function requireLogin() {
  if (isSessionValid()) {
    loginRedirected = false
    return true
  }
  if (!loginRedirected) {
    loginRedirected = true
    uni.navigateTo({ url: LOGIN_PAGE })
  }
  return false
}

async function loadPage(reset = false) {
  if (loading.value) {
    return
  }
  if (!requireLogin()) {
    // 未登录或登录态失效：不是「没有卡」，页面上给去登录的入口
    needLogin.value = true
    error.value = false
    return
  }
  needLogin.value = false
  if (!reset && pageNum.value > 0 && !hasMore.value) {
    return
  }
  loading.value = true
  error.value = false
  const nextPage = reset ? 1 : pageNum.value + 1
  try {
    const result = await listMemberCards({ pageNum: nextPage, pageSize: PAGE_SIZE })
    cards.value = reset ? result.slice() : cards.value.concat(result)
    pageNum.value = nextPage
    total.value = result.total
  } catch (requestError) {
    // 401 已由 request 统一清理登录态并提示重新登录；其余失败留在页面上给重试入口，不当成无卡
    error.value = true
  } finally {
    loading.value = false
  }
}

/** 重试：首屏失败从第 1 页重来，翻页失败只补当前这一页 */
function reload() {
  loadPage(pageNum.value === 0)
}

/** 未登录态的入口：登录页会拦截重复入栈之外的情况，这里只做一次跳转 */
function goLogin() {
  uni.navigateTo({ url: LOGIN_PAGE })
}

onLoad(() => {
  loadPage(true)
})

/**
 * 页面恢复（切后台跨天、后台开卡／停卡后返回）时重新校验登录态并刷新首屏，
 * 剩余天数与展示状态都由后端按请求时间实时判定，不刷新就会是旧值。
 */
onShow(() => {
  if (firstShow) {
    firstShow = false
    return
  }
  loadPage(true)
})

onPullDownRefresh(async () => {
  await loadPage(true)
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  loadPage()
})
</script>

<style lang="scss" scoped>
.cards {
  position: relative;
  min-height: 100vh;
  background: $ys-page;
}

/* 顶部浅蓝装饰底：截图里导航栏与首卡之间有一段浅蓝渐变（用纯色渐变近似原图的场景图） */
.cards__bg {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  height: 300rpx;
  background: linear-gradient(180deg, #bfe0f7 0%, #e4f1fb 58%, rgba(247, 249, 252, 0) 100%);
}

.cards__list {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
  padding-top: 28rpx;
}

.cards__empty {
  position: relative;
}

/* 查询失败：提示 + 重试按钮，与「暂无会员卡」的空态区分开 */
.cards__error {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 200rpx;
}

.cards__error-text {
  font-size: 27rpx;
  line-height: 1;
  color: $ys-ink-3;
}

.cards__retry {
  margin-top: 32rpx;
  padding: 16rpx 44rpx;
  background: $ys-brand;
  border-radius: 36rpx;
}

.cards__retry-text {
  font-size: 27rpx;
  line-height: 1;
  color: #ffffff;
}

.cards__state {
  position: relative;
  display: flex;
  justify-content: center;
  padding-top: 40rpx;
}

.cards__state-text {
  font-size: 25rpx;
  line-height: 1;
  color: $ys-ink-3;
}

.cards__foot {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 44rpx;

  /* 翻页失败：整行可点，点一次补当前页 */
  &--retry {
    padding-bottom: 12rpx;
  }
}

.cards__foot-line {
  width: 64rpx;
  height: 1px;
  background: #dde1e6;
}

.cards__foot-text {
  margin: 0 18rpx;
  font-size: 23rpx;
  line-height: 1;
  color: #b9bec6;
}

.cards__bottom {
  height: 48rpx;
}
</style>
