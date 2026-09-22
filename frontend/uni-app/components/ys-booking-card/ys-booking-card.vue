<template>
  <view class="ys-booking press" @tap="onTap">
    <view class="ys-booking__head">
      <text class="ys-booking__name">{{ item.courseName }}</text>
      <text class="ys-booking__status" :class="statusClass">{{ item.statusLabel }}</text>
    </view>

    <view class="ys-booking__row">
      <text class="ys-booking__row-text">{{ item.date }} {{ item.startTime }}-{{ item.endTime }}</text>
    </view>

    <view class="ys-booking__row">
      <text class="ys-booking__row-text">{{ item.coachName }} · {{ item.room }}</text>
    </view>

    <view class="ys-booking__row ys-booking__row--last">
      <text class="ys-booking__row-text">{{ item.storeName }}</text>
    </view>

    <view class="ys-booking__foot">
      <text class="ys-booking__card">{{ item.cardName }}</text>
      <view v-if="canCancel" class="ys-booking__btn" @tap.stop="onCancel">
        <text>取消预约</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 我的预约卡片
 *
 * 注意：原型「我的预约」页在截图里是空态，**没有给卡片样式**，
 * 这里的排版按设计 token 推导；原型补出卡片后以此处为唯一改动点。
 */
const props = defineProps({
  item: { type: Object, required: true }
})

const emit = defineEmits(['tap', 'cancel'])

/** 已预约（1）可取消；已取消（2）/已签到（3）不可 */
const canCancel = computed(() => Number(props.item.status) === 1)

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

function onTap() {
  emit('tap', props.item)
}

function onCancel() {
  emit('cancel', props.item)
}
</script>

<style lang="scss" scoped>
.ys-booking {
  padding: 25rpx;
  background: #fff;
  border-radius: $ys-radius-lg;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__name {
    font-size: 31rpx;
    font-weight: 600;
    color: $ys-ink-1;
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
      color: $ys-ink-4;
    }
  }

  &__row {
    margin-top: 13rpx;

    &--last {
      margin-bottom: 20rpx;
    }
  }

  &__row-text {
    font-size: 25rpx;
    color: $ys-ink-3;
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-top: 20rpx;
    border-top: 1px solid $ys-line;
  }

  &__card {
    font-size: 23rpx;
    color: $ys-ink-3;
  }

  &__btn {
    padding: 8rpx 23rpx;
    font-size: 25rpx;
    color: $ys-ink-2;
    border: 1px solid $ys-line;
    border-radius: 27rpx;
  }
}
</style>
