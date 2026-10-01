<template>
  <!--
    课程详情页（W6 · 新建）
    路由参数：scheduleId（字符串形式的雪花 ID）
    数据：GET /api/schedules/{scheduleId}

    口径（用户端接口详细设计 §2.3）：
      - 课种 courseType / courseTypeName **取排课快照**；难度 / 封面 / 介绍实时取课程当前值；
      - 状态文案 statusText 与 bookable 全部用后端返回值；
      - 预约人员 bookers 一期固定为 []（不是 null）→ 显示空态；
      - 「立即预约」只提示「敬请期待」，**不发起任何请求**；
      - 后端对「不存在 / 已删除 / 待上架」一律返回 404 → 友好提示并返回上一页。
  -->
  <view class="detail">
    <view v-if="detail" class="detail__body">
      <image class="detail__cover" :src="coverOf(detail)" mode="aspectFill" />

      <!-- 课程资料 -->
      <view class="card card--first">
        <view class="card__head">
          <text class="card__title">{{ detail.courseName }}</text>
          <text class="card__status" :class="statusClass(detail)">{{ detail.statusText }}</text>
        </view>
        <view class="card__meta">
          <text class="card__tag">{{ detail.courseTypeName }}</text>
          <text class="card__dot">·</text>
          <text class="card__meta-text">难度 {{ detail.difficulty }}</text>
        </view>
        <text class="card__intro">{{ introText }}</text>
      </view>

      <!-- 本次上课 -->
      <view class="card">
        <text class="card__section">本次上课</text>
        <view class="row">
          <text class="row__label">时间</text>
          <text class="row__value">{{ timeText }}</text>
        </view>
        <view class="row">
          <text class="row__label">门店</text>
          <text class="row__value">{{ detail.storeName || '门店待定' }}</text>
        </view>
        <view class="row">
          <text class="row__label">教室</text>
          <text class="row__value">{{ detail.classroomName || '教室待定' }}</text>
        </view>
      </view>

      <!-- 教练 -->
      <view class="card">
        <text class="card__section">上课教练</text>
        <view class="coach">
          <image
            v-if="detail.coachAvatarUrl"
            class="coach__avatar"
            :src="detail.coachAvatarUrl"
            mode="aspectFill"
          />
          <view class="coach__main">
            <text class="coach__name">{{ detail.coachName || '教练待定' }}</text>
            <text class="coach__intro">{{ detail.coachIntro || '暂无简介' }}</text>
          </view>
        </view>
      </view>

      <!-- 人数信息 -->
      <view class="card">
        <text class="card__section">人数信息</text>
        <view class="row">
          <text class="row__label">最大人数</text>
          <text class="row__value">{{ detail.maxPersons }} 人</text>
        </view>
        <view class="row">
          <text class="row__label">最低开课人数</text>
          <text class="row__value">{{ detail.minPersons }} 人</text>
        </view>
        <view class="row">
          <text class="row__label">剩余名额</text>
          <text class="row__value row__value--strong">{{ detail.remainingPlaces }} 个</text>
        </view>

        <text class="card__sub">预约人员</text>
        <view v-if="bookers.length" class="bookers">
          <view v-for="(booker, index) in bookers" :key="index" class="bookers__item">
            <text class="bookers__name">{{ bookerName(booker) }}</text>
          </view>
        </view>
        <!-- 一期没有预约：后端固定返回 []，这里显示空态 -->
        <view v-else class="bookers__empty">
          <text class="bookers__empty-text">暂无预约人员</text>
        </view>
      </view>

      <view class="detail__space" />
    </view>

    <!-- 排课不存在 / 已删除 / 待上架（后端统一 404）→ 友好提示并返回上一页 -->
    <view v-else-if="loadError" class="detail__error">
      <ys-empty
        image="/static/images/empty/booking.png"
        image-width="265rpx"
        image-height="108rpx"
        title="课程不存在或已下架"
        subtitle="即将返回上一页"
        :padding-top="120"
      />
      <view class="detail__error-btn press" @tap="goBack">
        <text>返回</text>
      </view>
    </view>

    <!-- 底部操作条 -->
    <view v-if="detail" class="bar">
      <view class="bar__btn" :class="{ 'bar__btn--off': !detail.bookable }" @tap="onBook">
        <text>{{ detail.bookable ? '立即预约' : '不可预约' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { getScheduleDetail } from '@/api/schedule'

/**
 * 课程详情页（W6）
 *
 * 一期没有预约：「立即预约」只在 bookable 为 true 时可点，点击仅提示「敬请期待」，不发请求。
 * 状态文案与 bookable 一律来自后端，前端不推导。
 */

const scheduleId = ref('')
const detail = ref(null)
const loadError = ref(false)

let backTimer = null

const introText = computed(() => {
  const text = detail.value ? detail.value.courseIntro : ''
  return text || '暂无课程介绍'
})

const timeText = computed(() => {
  if (!detail.value) {
    return ''
  }
  const date = detail.value.scheduleDate || ''
  const start = hm(detail.value.startTime)
  const end = hm(detail.value.endTime)
  const range = start && end ? start + '-' + end : start || end || '时间待定'
  return date ? date + ' ' + range : range
})

/** 预约人员（一期固定空数组） */
const bookers = computed(() => {
  const list = detail.value ? detail.value.bookers : null
  return Array.isArray(list) ? list : []
})

function hm(value) {
  const text = String(value || '')
  return text.length >= 16 ? text.slice(11, 16) : text
}

/** 封面：后端未配图时用原型封面占位（不造数据，只兜底视觉） */
function coverOf(item) {
  return item.courseCoverUrl || '/static/images/booking/cover-1.jpg'
}

/** 状态徽标配色由后端文案决定（可约 = 蓝，已取消 = 灰红，其余 = 灰） */
function statusClass(item) {
  if (item.bookable) {
    return 'card__status--on'
  }
  return item.statusText === '已取消' ? 'card__status--cancel' : 'card__status--off'
}

function bookerName(booker) {
  if (!booker) {
    return ''
  }
  if (typeof booker === 'string') {
    return booker
  }
  return booker.name || booker.nickName || ''
}

onLoad((query) => {
  scheduleId.value = query && query.scheduleId ? String(query.scheduleId) : ''
  loadDetail()
})

onUnload(() => {
  if (backTimer) {
    clearTimeout(backTimer)
    backTimer = null
  }
})

async function loadDetail() {
  if (!scheduleId.value) {
    onNotFound()
    return
  }
  try {
    const data = await getScheduleDetail(scheduleId.value)
    if (!data) {
      onNotFound()
      return
    }
    detail.value = data
  } catch (error) {
    // 404（不存在 / 已删除 / 待上架）与 500（参数非法）都由 utils/request 统一 toast 服务端 msg
    onNotFound()
  }
}

/** 友好提示 + 自动返回上一页（没有上一页时回约课 Tab） */
function onNotFound() {
  loadError.value = true
  if (backTimer) {
    clearTimeout(backTimer)
  }
  backTimer = setTimeout(() => {
    backTimer = null
    goBack()
  }, 1500)
}

function goBack() {
  const pages = getCurrentPages()
  if (pages && pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.switchTab({ url: '/pages/booking/index' })
}

/** 一期没有预约：只提示「敬请期待」，不发任何请求 */
function onBook() {
  if (!detail.value || !detail.value.bookable) {
    return
  }
  uni.showToast({ title: '敬请期待', icon: 'none' })
}
</script>

<style lang="scss" scoped>
.detail {
  min-height: 100vh;
  background: $ys-page;

  &__body {
    padding-bottom: 160rpx;
  }

  &__cover {
    display: block;
    width: 100%;
    height: 420rpx;
    background: $ys-line;
  }

  &__space {
    height: 20rpx;
  }

  &__error {
    padding-top: 80rpx;
  }

  &__error-btn {
    margin: 60rpx auto 0;
    width: 300rpx;
    height: 80rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 29rpx;
    color: #fff;
    background: $ys-brand;
    border-radius: 56rpx;
  }
}

/* ---------------- 卡片 ---------------- */
.card {
  margin: 20rpx $ys-gap 0;
  padding: 28rpx 25rpx;
  background: #fff;
  border-radius: $ys-radius-lg;
  box-shadow: $ys-card-shadow;

  &--first {
    margin-top: 24rpx;
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__title {
    flex: 1;
    font-size: 35rpx;
    font-weight: 600;
    line-height: 1.3;
    color: $ys-title;
  }

  &__status {
    flex: none;
    margin-left: 15rpx;
    padding: 4rpx 14rpx;
    font-size: 23rpx;
    line-height: 1.4;
    border-radius: 8rpx;

    &--on {
      color: $ys-brand;
      background: $ys-brand-pale;
    }

    &--off {
      color: $ys-ink-3;
      background: $ys-line;
    }

    &--cancel {
      color: #b25a5a;
      background: #f6e7e7;
    }
  }

  &__meta {
    display: flex;
    align-items: center;
    margin-top: 16rpx;
  }

  &__tag {
    padding: 2rpx 12rpx;
    font-size: 23rpx;
    line-height: 1.4;
    color: $ys-brand;
    background: $ys-brand-pale;
    border-radius: 8rpx;
  }

  &__dot {
    margin: 0 10rpx;
    font-size: 23rpx;
    color: $ys-ink-4;
  }

  &__meta-text {
    font-size: 25rpx;
    color: $ys-ink-3;
  }

  &__intro {
    display: block;
    margin-top: 18rpx;
    font-size: 27rpx;
    line-height: 1.6;
    color: $ys-ink-2;
  }

  &__section {
    display: block;
    font-size: 29rpx;
    font-weight: 600;
    line-height: 1.3;
    color: $ys-title;
  }

  &__sub {
    display: block;
    margin-top: 28rpx;
    font-size: 27rpx;
    font-weight: 500;
    line-height: 1.3;
    color: $ys-title;
  }
}

/* ---------------- 信息行 ---------------- */
.row {
  display: flex;
  align-items: flex-start;
  margin-top: 22rpx;

  &__label {
    flex: none;
    width: 190rpx;
    font-size: 27rpx;
    line-height: 1.4;
    color: $ys-ink-3;
  }

  &__value {
    flex: 1;
    font-size: 27rpx;
    line-height: 1.4;
    color: $ys-ink-1;

    &--strong {
      font-weight: 600;
      color: $ys-brand;
    }
  }
}

/* ---------------- 教练 ---------------- */
.coach {
  display: flex;
  align-items: center;
  margin-top: 22rpx;

  &__avatar {
    flex: none;
    width: 110rpx;
    height: 110rpx;
    border-radius: $ys-radius-md;
    background: $ys-line;
  }

  &__main {
    flex: 1;
    margin-left: 23rpx;
    display: flex;
    flex-direction: column;
  }

  &__name {
    font-size: 29rpx;
    font-weight: 600;
    line-height: 1.3;
    color: $ys-ink-1;
  }

  &__intro {
    margin-top: 10rpx;
    font-size: 25rpx;
    line-height: 1.5;
    color: $ys-ink-3;
  }
}

/* ---------------- 预约人员 ---------------- */
.bookers {
  margin-top: 18rpx;

  &__item {
    padding: 14rpx 0;
    border-bottom: 1px solid $ys-line;
  }

  &__name {
    font-size: 27rpx;
    line-height: 1.3;
    color: $ys-ink-2;
  }

  &__empty {
    margin-top: 18rpx;
    padding: 30rpx 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: $ys-page;
    border-radius: $ys-radius-sm;
  }

  &__empty-text {
    font-size: 25rpx;
    line-height: 1;
    color: $ys-ink-3;
  }
}

/* ---------------- 底部操作条 ---------------- */
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 20rpx 25rpx 30rpx;
  background: #fff;
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.05);

  &__btn {
    height: 88rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 31rpx;
    font-weight: 500;
    color: #fff;
    background: $ys-brand;
    border-radius: 56rpx;

    /* 已结束 / 已取消（bookable = false）：不可预约 */
    &--off {
      background: #c9d3dd;
    }
  }
}
</style>
