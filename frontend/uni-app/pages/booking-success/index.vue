<template>
  <!--
    预约成功页

    由立即预约页 redirectTo 进入（不带业务数据，只带展示用的 query）：
    从成功页返回不会回到立即预约页，因此这里只做结果告知与两个出口。
  -->
  <view class="ok">
    <view class="ok__main">
      <view class="ok__mark">
        <uni-icons type="checkmarkempty" size="44" color="#FFFFFF" />
      </view>

      <text class="ok__title">预约成功</text>
      <text class="ok__subtitle">{{ subtitle }}</text>
    </view>

    <view class="ok__actions">
      <view class="ok__btn ok__btn--primary press" @tap="goMyBookings">
        <text class="ok__btn-text">查看预约</text>
      </view>
      <view class="ok__btn ok__btn--ghost press" @tap="goHome">
        <text class="ok__btn-text ok__btn-text--ghost">返回首页</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

/** 预约成功页 */

const courseName = ref('')
const scheduleDate = ref('')
const startTime = ref('')

/** 带 query 时给出「课程名 · 日期 时间」，否则只说明结果 */
const subtitle = computed(() => {
  const parts = []
  if (courseName.value) {
    parts.push(courseName.value)
  }
  const when = [scheduleDate.value, startTime.value].filter(Boolean).join(' ')
  if (when) {
    parts.push(when)
  }
  if (!parts.length) {
    return '已加入我的预约，可前往查看详情'
  }
  return parts.join(' · ')
})

onLoad((query) => {
  const params = query || {}
  courseName.value = params.courseName ? String(params.courseName) : ''
  scheduleDate.value = params.scheduleDate ? String(params.scheduleDate) : ''
  startTime.value = params.startTime ? String(params.startTime) : ''
})

function goMyBookings() {
  uni.switchTab({ url: '/pages/my-bookings/index' })
}

function goHome() {
  uni.switchTab({ url: '/pages/home/index' })
}
</script>

<style lang="scss" scoped>
.ok {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: $ys-page;

  &__main {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 0 60rpx;
  }

  /* 成功标记：纯 CSS 画的圆 + uni-icons 里的对勾字形 */
  &__mark {
    width: 160rpx;
    height: 160rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    background: $ys-brand;
    border-radius: 50%;
    box-shadow: 0 8rpx 24rpx rgba(96, 156, 233, 0.35);
  }

  &__title {
    margin-top: 44rpx;
    font-size: 40rpx;
    font-weight: 600;
    line-height: 1.3;
    color: #303339;
  }

  &__subtitle {
    margin-top: 20rpx;
    text-align: center;
    font-size: 27rpx;
    line-height: 1.5;
    color: $ys-ink-3;
  }

  &__actions {
    padding: 0 60rpx 80rpx;
  }

  &__btn {
    height: 88rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 44rpx;

    &--primary {
      background: $ys-brand-light;
    }

    &--ghost {
      margin-top: 24rpx;
      background: #fff;
      border: 1px solid $ys-brand-light;
    }
  }

  &__btn-text {
    font-size: 31rpx;
    font-weight: 500;
    color: #fff;

    &--ghost {
      color: $ys-brand;
    }
  }
}
</style>
