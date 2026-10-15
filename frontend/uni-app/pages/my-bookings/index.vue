<template>
  <!--
    我的预约

    数据：GET /api/reservations（status ＋ cardType ＋ 分页），取消与签到都是真实接口。
    状态栏 / 导航栏 / 微信胶囊 / TabBar 由系统绘制，本页只画内容区。
  -->
  <view class="bookings">
    <!-- ===== 上方白色区：状态筛选 Tab + 卡类型筛选 ===== -->
    <view class="head">
      <!-- 状态筛选 Tab：全部 / 已预约 / 已取消 / 已签到
           选中态 = 文字加深加粗 + 其下蓝色圆角下划线 -->
      <view class="status">
        <view
          v-for="tab in statusTabs"
          :key="tab.value"
          class="status__item press"
          @tap="onStatusChange(tab.value)"
        >
          <text class="status__text" :class="{ 'status__text--on': tab.value === activeStatus }">
            {{ tab.label }}
          </text>
          <view class="status__bar" :class="{ 'status__bar--on': tab.value === activeStatus }" />
        </view>
      </view>

      <!-- 卡类型下拉：次数卡 / 年卡 -->
      <view class="filters">
        <view class="filters__btn press" @tap="onCardTypeTap">
          <uni-icons class="filters__icon" type="tune" size="15" color="#609CE9" />
          <text class="filters__label">{{ cardTypeLabel }}</text>
          <uni-icons class="filters__caret" type="bottom" size="9" color="#999999" />
        </view>
      </view>
    </view>

    <!-- 未登录、加载失败与「确实没有预约」是三种状态：只有成功且结果为空才显示空态 -->
    <view v-if="needLogin" class="state">
      <text class="state__text">登录后查看我的预约</text>
      <view class="state__btn press" @tap="goLogin">
        <text class="state__btn-text">去登录</text>
      </view>
    </view>
    <view v-else-if="error && !bookings.length" class="state">
      <text class="state__text">预约记录加载失败，请稍后重试</text>
      <view class="state__btn press" @tap="reload">
        <text class="state__btn-text">重新加载</text>
      </view>
    </view>
    <view v-else-if="!bookings.length && !loading" class="empty-box">
      <ys-empty
        image="/static/images/empty/booking.png"
        image-width="265rpx"
        image-height="108rpx"
        title="暂无符合条件的预约"
        subtitle="可切换预约状态或卡类型查看"
        :padding-top="181"
      />
    </view>

    <view v-else class="list">
      <ys-booking-card
        v-for="item in bookings"
        :key="item.id"
        :item="item"
        @cancel="onCancel"
        @checkin="onCheckIn"
      />
    </view>

    <view v-if="loading" class="foot">
      <text class="foot__text">加载中...</text>
    </view>
    <view v-else-if="error && bookings.length && !needLogin" class="foot foot--retry" @tap="reload">
      <text class="foot__text">加载失败，点击重试</text>
    </view>
    <view v-else-if="bookings.length && !hasMore && !needLogin" class="foot">
      <view class="foot__line" />
      <text class="foot__text">已经到底了</text>
      <view class="foot__line" />
    </view>

    <view class="bottom" />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onReachBottom, onShow } from '@dcloudio/uni-app'
import {
  getBookingStatusTabs,
  getCardTypeFilters,
  listBookings,
  cancelBooking,
  checkInBooking
} from '@/api/booking'
import { isSessionValid, LOGIN_PAGE } from '@/utils/session'

/** 我的预约 */

const PAGE_SIZE = 20

const statusTabs = ref(getBookingStatusTabs())
const cardTypes = ref(getCardTypeFilters())

const bookings = ref([])
const pageNum = ref(0)
const total = ref(0)
const loading = ref(false)
const error = ref(false)
const needLogin = ref(false)

const activeStatus = ref(0)
const activeCardType = ref(0)

const cardTypeLabel = computed(() => {
  if (!activeCardType.value) {
    return '全部卡类型'
  }
  const hit = cardTypes.value.find((item) => item.value === activeCardType.value)
  return hit ? hit.label : '全部卡类型'
})

/** 后端已按预约时间倒序返回，翻页只按已加载条数与 total 比较 */
const hasMore = computed(() => bookings.value.length < total.value)

/** 未登录只跳一次登录页，避免 onShow 反复入栈 */
let loginRedirected = false

/** 请求序号：只有最新一次请求的结果能写回列表，过期响应直接丢弃 */
let requestSeq = 0

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
  if (!requireLogin()) {
    needLogin.value = true
    error.value = false
    bookings.value = []
    total.value = 0
    pageNum.value = 0
    return
  }
  // 追加下一页时不重复发起；筛选变化（reset）允许插队重新加载
  if (!reset && (loading.value || (pageNum.value > 0 && !hasMore.value))) {
    return
  }
  needLogin.value = false
  const seq = ++requestSeq
  const nextPage = reset ? 1 : pageNum.value + 1
  loading.value = true
  error.value = false
  try {
    const result = await listBookings({
      status: activeStatus.value,
      cardType: activeCardType.value,
      pageNum: nextPage,
      pageSize: PAGE_SIZE
    })
    if (seq !== requestSeq) {
      return
    }
    bookings.value = reset ? result.list : bookings.value.concat(result.list)
    pageNum.value = nextPage
    total.value = result.total
  } catch (requestError) {
    // 401 已由 request 统一清理登录态并提示重新登录；其余失败留在页面上给重试入口，不当成空列表
    if (seq === requestSeq) {
      error.value = true
    }
  } finally {
    if (seq === requestSeq) {
      loading.value = false
    }
  }
}

/** 切换筛选：先清空列表再重新加载，旧筛选的数据不停留在页面上 */
function applyFilter() {
  bookings.value = []
  total.value = 0
  pageNum.value = 0
  loadPage(true)
}

/** 重试：首屏失败从第 1 页重来，翻页失败只补当前这一页 */
function reload() {
  loadPage(pageNum.value === 0)
}

function goLogin() {
  uni.navigateTo({ url: LOGIN_PAGE })
}

function onStatusChange(value) {
  if (value === activeStatus.value) {
    return
  }
  activeStatus.value = value
  applyFilter()
}

function onCardTypeTap() {
  uni.showActionSheet({
    itemList: cardTypes.value.map((item) => item.label),
    success: (res) => {
      const picked = cardTypes.value[res.tapIndex]
      if (!picked || picked.value === activeCardType.value) {
        return
      }
      activeCardType.value = picked.value
      applyFilter()
    }
  })
}

// 用 onShow：从约课页预约成功切回来时列表要刷新
onShow(() => {
  loadPage(true)
})

onReachBottom(() => {
  loadPage()
})

/** 「取消预约」→ 确认弹窗 → cancelBooking → toast 后刷新列表 */
function onCancel(item) {
  uni.showModal({
    title: '取消预约',
    content: '确定要取消「' + item.courseName + '」的预约吗？',
    confirmText: '确定',
    cancelText: '取消',
    success: async (res) => {
      if (!res.confirm) {
        return
      }
      try {
        const message = await cancelBooking(item.id)
        uni.showToast({ title: message || '取消预约成功' })
        await loadPage(true)
      } catch (requestError) {
        // 失败原因（超过取消截止时间 / 已签到 / 排课已结束）已由 request 统一 toast
      }
    }
  })
}

/** 「签到」→ checkInBooking → toast 后刷新列表 */
async function onCheckIn(item) {
  try {
    const message = await checkInBooking(item.id)
    uni.showToast({ title: message || '签到成功' })
    await loadPage(true)
  } catch (requestError) {
    // 失败原因（未到签到窗口 / 课程已结束 / 已取消）已由 request 统一 toast
  }
}
</script>

<style lang="scss" scoped>
.bookings {
  min-height: 100vh;
  background: $ys-page;
}

/* ---------------- 上方白底区 ---------------- */
.head {
  background: #fff;
}

/* 状态筛选 Tab：整行 52pt，文字底对齐 */
.status {
  display: flex;
  align-items: flex-end;
  height: 100rpx;
  padding: 0 31rpx;

  &__item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  &__text {
    font-size: 29rpx;
    line-height: 1;
    color: $ys-ink-3;

    &--on {
      font-weight: 600;
      color: $ys-ink-1;
    }
  }

  /* 蓝色圆角下划线；未选中占位用透明块，保证两态基线一致 */
  &__bar {
    margin-top: 13rpx;
    width: 46rpx;
    height: 6rpx;
    border-radius: 6rpx;
    background: transparent;

    &--on {
      background: $ys-brand;
    }
  }
}

/* 卡类型下拉行：右对齐 */
.filters {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  height: 85rpx;
  padding: 0 31rpx;

  &__btn {
    display: flex;
    align-items: center;
    box-sizing: border-box;
    height: 54rpx;
    padding: 0 15rpx;
    border: 1px solid $ys-line;
    border-radius: 12rpx;
    background: #fff;
  }

  &__icon {
    flex: none;
  }

  &__label {
    margin-left: 6rpx;
    font-size: 23rpx;
    line-height: 1;
    color: $ys-ink-2;
  }

  &__caret {
    margin-left: 8rpx;
    flex: none;
  }
}

/* ---------------- 空态 ---------------- */
.empty-box {
  background: #fff;
}

/* ---------------- 未登录 / 加载失败 ---------------- */
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 200rpx;

  &__text {
    font-size: 27rpx;
    line-height: 1;
    color: $ys-ink-3;
  }

  &__btn {
    margin-top: 32rpx;
    padding: 16rpx 44rpx;
    background: $ys-brand;
    border-radius: 36rpx;
  }

  &__btn-text {
    font-size: 27rpx;
    line-height: 1;
    color: #fff;
  }
}

/* ---------------- 预约卡列表：页面左右 13pt、卡片间距 8pt ---------------- */
.list {
  /* 卡片间距用 flex + gap：WXSS 不支持星号通配符选择器 */
  display: flex;
  flex-direction: column;
  gap: 15rpx;
  padding: 31rpx $ys-gap 0;
}

/* ---------------- 底部：加载中 / 重试 / 到底 ---------------- */
.foot {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 44rpx;

  /* 翻页失败：整行可点，点一次补当前页 */
  &--retry {
    padding-bottom: 12rpx;
  }
}

.foot__line {
  width: 64rpx;
  height: 1px;
  background: #dde1e6;
}

.foot__text {
  margin: 0 18rpx;
  font-size: 23rpx;
  line-height: 1;
  color: #b9bec6;
}

.bottom {
  height: 48rpx;
}
</style>
