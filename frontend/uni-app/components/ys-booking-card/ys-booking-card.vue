<template>
  <view class="ys-booking">
    <view class="ys-booking__left">
      <image class="ys-booking__cover" :src="coverOf" mode="aspectFill" />
      <text class="ys-booking__coach">{{ item.coachName || '教练待定' }}</text>
    </view>

    <view class="ys-booking__main">
      <view class="ys-booking__head">
        <text class="ys-booking__tag">{{ item.courseTypeName }}</text>
        <text class="ys-booking__name">{{ item.courseName }}</text>
      </view>

      <view class="ys-booking__line">
        <text class="ys-booking__status" :class="statusClass">{{ item.statusText }}</text>
        <text class="ys-booking__date">{{ item.scheduleDate }}</text>
      </view>

      <text class="ys-booking__line">上课 {{ item.startHm }} - {{ item.endHm }}</text>
      <text class="ys-booking__line ys-booking__line--addr">{{ item.storeAddress || '门店地址待定' }}</text>

      <view v-if="isBooked" class="ys-booking__actions">
        <view class="ys-booking__cancel press" @tap="onCancel">
          <text class="ys-booking__cancel-text">取消预约</text>
        </view>
        <view
          class="ys-booking__checkin press"
          :class="{ 'ys-booking__checkin--off': !canCheckIn }"
          @tap="onCheckIn"
        >
          <text class="ys-booking__checkin-text">签到</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'

/**
 * 我的预约卡片
 *
 * 取数范围是 GET /api/reservations 的 ReservationPublicItemVO；
 * 整卡不响应点击，只有「取消预约」与「签到」两个按钮可交互；
 * 签到窗口（课前 30 分钟起、课程结束前）按响应式时钟预判，后端签到仍会再校验。
 */
const props = defineProps({
  item: { type: Object, required: true }
})

const emit = defineEmits(['cancel', 'checkin'])

/** 签到窗口：课程开始前 30 分钟开放 */
const CHECK_IN_OPEN_MINUTES = 30

/** 时钟刷新间隔：页面停留在签到窗口前时，窗口一到按钮就能点 */
const CLOCK_TICK_MS = 10000

const now = ref(Date.now())

let clockTimer = null

onMounted(() => {
  clockTimer = setInterval(() => {
    now.value = Date.now()
  }, CLOCK_TICK_MS)
})

onUnmounted(() => {
  if (clockTimer) {
    clearInterval(clockTimer)
    clockTimer = null
  }
})

/** 已预约（1）可取消、可签到；已取消（2）/已签到（3）都不再显示操作 */
const isBooked = computed(() => Number(props.item.status) === 1)

/** 到签到窗口才让点：当前时间 >= 开课时间 - 30 分钟，且 < 结束时间 */
const canCheckIn = computed(() => {
  const start = parseTime(props.item.startTime)
  const end = parseTime(props.item.endTime)
  if (start === null || end === null) {
    return false
  }
  const current = now.value
  return current >= start - CHECK_IN_OPEN_MINUTES * 60 * 1000 && current < end
})

const coverOf = computed(() => props.item.courseCoverUrl || '/static/images/booking/cover-1.jpg')

const statusClass = computed(() => {
  const status = Number(props.item.status)
  if (status === 1) {
    return 'ys-booking__status--active'
  }
  if (status === 3) {
    return 'ys-booking__status--done'
  }
  return 'ys-booking__status--cancel'
})

/** 把 yyyy-MM-dd HH:mm:ss 解析成毫秒时间戳；解析不出来返回 null */
function parseTime(value) {
  const text = String(value || '').replace(/-/g, '/')
  const stamp = new Date(text).getTime()
  return isNaN(stamp) ? null : stamp
}

function onCancel() {
  emit('cancel', props.item)
}

function onCheckIn() {
  if (!canCheckIn.value) {
    return
  }
  emit('checkin', props.item)
}
</script>

<style lang="scss" scoped>
.ys-booking {
  display: flex;
  padding: 25rpx;
  background: #fff;
  border-radius: $ys-radius-lg;

  &__left {
    flex: none;
    width: 152rpx;
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  &__cover {
    width: 152rpx;
    height: 152rpx;
    border-radius: $ys-radius-md;
    background: $ys-line;
  }

  &__coach {
    margin-top: 12rpx;
    max-width: 152rpx;
    font-size: 23rpx;
    line-height: 1.3;
    color: $ys-ink-2;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__main {
    flex: 1;
    margin-left: 22rpx;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  &__head {
    display: flex;
    align-items: center;
  }

  &__tag {
    flex: none;
    padding: 2rpx 10rpx;
    font-size: 21rpx;
    line-height: 1.4;
    color: #fff;
    background: $ys-brand;
    border-radius: 8rpx;
  }

  &__name {
    flex: 1;
    margin-left: 12rpx;
    font-size: 31rpx;
    font-weight: 600;
    line-height: 1.3;
    color: $ys-ink-1;
  }

  &__line {
    margin-top: 12rpx;
    font-size: 25rpx;
    line-height: 1.4;
    color: $ys-ink-2;

    &--addr {
      color: $ys-ink-3;
    }
  }

  &__status {
    font-size: 25rpx;

    &--active {
      color: $ys-brand;
    }

    &--done {
      color: $ys-ink-3;
    }

    &--cancel {
      color: #e54545;
    }
  }

  &__date {
    margin-left: 10rpx;
    font-size: 25rpx;
    line-height: 1.4;
    color: $ys-ink-3;
  }

  &__actions {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    margin-top: 18rpx;
  }

  &__cancel {
    padding: 0 18rpx;
  }

  &__cancel-text {
    font-size: 25rpx;
    line-height: 1.4;
    color: $ys-ink-2;
  }

  &__checkin {
    margin-left: 18rpx;
    padding: 10rpx 40rpx;
    background: $ys-brand;
    border-radius: 56rpx;

    /* 未到签到窗口：置灰且不可点 */
    &--off {
      background: #c9d3dd;
    }
  }

  &__checkin-text {
    font-size: 25rpx;
    line-height: 1;
    color: #fff;
  }
}
</style>
