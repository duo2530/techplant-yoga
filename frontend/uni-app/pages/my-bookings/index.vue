<template>
  <!--
    我的预约（严格照 docs/原型/my-bookings.html）

    状态栏 / 导航栏 / 微信胶囊 / TabBar 由小程序绘制，本页不画。
    导航栏标题「我的预约」已在 pages.json 配好；原型这一页导航栏左侧没有任何控件。
  -->
  <view class="bookings">
    <!-- ===== 上方白色区：状态筛选 Tab + 计数/下拉筛选栏 ===== -->
    <view class="head">
      <!-- 状态筛选 Tab：全部 / 已预约 / 已取消 / 已签到
           选中态 = 文字加深加粗 + 其下 24×3pt 蓝色圆角下划线 -->
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

      <!-- 计数 + 两个下拉筛选 -->
      <view class="filters">
        <view class="filters__count">
          <text class="filters__num">{{ bookings.length }}</text>
          <text class="filters__unit">条预约</text>
        </view>
        <view class="filters__right">
          <!-- 下拉 1：课种。原型左侧是蓝色「文档/列表」图标；
               uni-icons 没有文档图标，取形状最接近的 compose（纸+笔），说明见交付报告 -->
          <view class="filters__btn press" @tap="onCourseTypeTap">
            <uni-icons class="filters__icon" type="compose" size="15" color="#609CE9" />
            <text class="filters__label">{{ courseTypeLabel }}</text>
            <uni-icons class="filters__caret" type="bottom" size="9" color="#999999" />
          </view>
          <!-- 下拉 2：卡种。原型左侧是蓝色漏斗图标；
               uni-icons 没有漏斗，取语义最接近的 tune（筛选/调节） -->
          <view class="filters__btn filters__btn--last press" @tap="onCardTypeTap">
            <uni-icons class="filters__icon" type="tune" size="15" color="#609CE9" />
            <text class="filters__label">{{ cardTypeLabel }}</text>
            <uni-icons class="filters__caret" type="bottom" size="9" color="#999999" />
          </view>
        </view>
      </view>
    </view>

    <!-- ===== 空态（原型：白盒 + 插画 + 两行文案，白盒距内容区顶 181pt） ===== -->
    <view v-if="!bookings.length" class="empty-box">
      <ys-empty
        image="/static/images/empty/booking.png"
        image-width="265rpx"
        image-height="108rpx"
        title="暂无符合条件的预约"
        subtitle="可切换预约状态、课种或卡种查看"
        :padding-top="181"
      />
    </view>

    <!-- ===== 有数据时（MOCK.empty.bookings = false）的预约卡列表 ===== -->
    <view v-else class="list">
      <ys-booking-card
        v-for="item in bookings"
        :key="item.id"
        :item="item"
        @tap="onBookingTap"
        @cancel="onCancel"
      />
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getBookingStatusTabs, getBookingFilters, listBookings, cancelBooking } from '@/api/booking'

/**
 * 我的预约
 *
 * 数据全部来自 @/api/booking（内部读 mock），不发任何网络请求。
 * 原型这一页是空态（暂无符合条件的预约），由 MOCK.empty.bookings 控制；
 * 把开关改成 false 就会走 ys-booking-card 列表分支。
 */

const statusTabs = ref([])
const courseTypes = ref([])
const cardTypes = ref([])
const bookings = ref([])

const activeStatus = ref(0)
const activeCourseType = ref(0)
const activeCardType = ref(0)

/** 原型两个下拉都显示原文「全部...」（末尾三个点，不是省略号字符） */
function filterLabel(list, value) {
  const hit = list.value.find((item) => item.value === value)
  if (!hit || value === 0) {
    return '全部...'
  }
  return hit.label + '...'
}

const courseTypeLabel = computed(() => filterLabel(courseTypes, activeCourseType.value))
const cardTypeLabel = computed(() => filterLabel(cardTypes, activeCardType.value))

async function loadBookings() {
  bookings.value = await listBookings({
    status: activeStatus.value,
    courseType: activeCourseType.value,
    cardType: activeCardType.value
  })
}

async function init() {
  const [tabs, filters] = await Promise.all([getBookingStatusTabs(), getBookingFilters()])
  statusTabs.value = tabs
  courseTypes.value = filters.courseTypes
  cardTypes.value = filters.cardTypes
  await loadBookings()
}

// 用 onShow：从约课页预约成功切回来时列表要刷新
onShow(() => {
  init()
})

function onStatusChange(value) {
  if (value === activeStatus.value) {
    return
  }
  activeStatus.value = value
  loadBookings()
}

function onCourseTypeTap() {
  if (!courseTypes.value.length) {
    return
  }
  uni.showActionSheet({
    itemList: courseTypes.value.map((item) => item.label),
    success: (res) => {
      const picked = courseTypes.value[res.tapIndex]
      if (!picked || picked.value === activeCourseType.value) {
        return
      }
      activeCourseType.value = picked.value
      loadBookings()
    }
  })
}

function onCardTypeTap() {
  if (!cardTypes.value.length) {
    return
  }
  uni.showActionSheet({
    itemList: cardTypes.value.map((item) => item.label),
    success: (res) => {
      const picked = cardTypes.value[res.tapIndex]
      if (!picked || picked.value === activeCardType.value) {
        return
      }
      activeCardType.value = picked.value
      loadBookings()
    }
  })
}

function onBookingTap() {
  // TODO 原型没有「预约详情」页，暂不跳转（getBookingDetail 已可用）
}

/** 「取消预约」→ 确认弹窗 → cancelBooking → toast */
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
      const result = await cancelBooking(item.id)
      if (result && result.success) {
        uni.showToast({ title: result.message || '已取消预约' })
        await loadBookings()
        return
      }
      uni.showToast({ title: (result && result.message) || '取消失败', icon: 'none' })
    }
  })
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

  /* 24×3pt 蓝色圆角下划线；未选中占位用透明块，保证两态基线一致 */
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

/* 计数 + 下拉筛选行：44pt */
.filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 85rpx;
  padding: 0 31rpx;

  &__count {
    display: flex;
    align-items: baseline;
  }

  &__num {
    font-size: 29rpx;
    font-weight: 600;
    line-height: 1;
    color: $ys-ink-1;
  }

  &__unit {
    margin-left: 6rpx;
    font-size: 25rpx;
    line-height: 1;
    color: $ys-ink-3;
  }

  &__right {
    display: flex;
    align-items: center;
  }

  &__btn {
    display: flex;
    align-items: center;
    box-sizing: border-box;
    height: 54rpx;
    padding: 0 15rpx;
    border: 1px solid $ys-line;
    border-radius: 12rpx;
    background: #fff;

    &--last {
      margin-left: 15rpx;
    }
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

/* ---------------- 预约卡列表：页面左右 13pt、卡片间距 8pt ---------------- */
.list {
  /* 卡片间距用 flex + gap：WXSS 不支持星号通配符选择器（会报 error at token） */
  display: flex;
  flex-direction: column;
  gap: 15rpx;
  padding: 31rpx $ys-gap 0;
}
</style>
