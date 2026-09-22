<template>
  <view class="ys-course press" @tap="onTap">
    <view class="ys-course__head">
      <text class="ys-course__name">{{ item.courseName }}</text>
      <text class="ys-course__time">{{ item.startTime }}-{{ item.endTime }}</text>
    </view>

    <view class="ys-course__meta">
      <text class="ys-course__tag">{{ courseTypeLabel }}</text>
      <text class="ys-course__dot">·</text>
      <text class="ys-course__meta-text">{{ item.durationMin }} 分钟</text>
      <text class="ys-course__dot">·</text>
      <text class="ys-course__meta-text">难度 {{ item.difficulty }} 星</text>
    </view>

    <view class="ys-course__foot">
      <view class="ys-course__coach">
        <image v-if="item.coachAvatar" class="ys-course__avatar" :src="item.coachAvatar" mode="aspectFill" />
        <text class="ys-course__coach-name">{{ item.coachName }}</text>
        <text class="ys-course__room">{{ item.room }}</text>
      </view>
      <view class="ys-course__action">
        <text class="ys-course__left" :class="{ 'ys-course__left--full': item.soldOut }">{{ leftText }}</text>
        <view
          class="ys-course__btn"
          :class="{ 'ys-course__btn--disabled': item.soldOut }"
          @tap.stop="onBook"
        >
          <text>{{ item.soldOut ? '已约满' : '预约' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { COURSE_TYPES } from '@/mock/course'

/**
 * 课程/场次卡片
 *
 * 注意：原型**没有给课程卡片样式**（首页与约课页在截图里都是空态/无卡片数据），
 * 这里的排版是按设计 token（白卡 + 品牌蓝 + 圆角 + 灰阶文字）推导的，
 * 原型补出课程卡后应以此处为唯一改动点。
 */
const props = defineProps({
  item: { type: Object, required: true }
})

const emit = defineEmits(['tap', 'book'])

const courseTypeLabel = computed(() => {
  const hit = COURSE_TYPES.find((type) => type.value === props.item.courseType)
  return hit ? hit.label : ''
})

const leftText = computed(() => {
  if (props.item.soldOut) {
    return '名额已满'
  }
  return '剩余 ' + props.item.left + '/' + props.item.capacity
})

function onTap() {
  emit('tap', props.item)
}

function onBook() {
  if (props.item.soldOut) {
    return
  }
  emit('book', props.item)
}
</script>

<style lang="scss" scoped>
.ys-course {
  padding: 25rpx;
  background: #fff;
  border-radius: $ys-radius-lg;
  box-shadow: $ys-card-shadow;

  &__head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
  }

  &__name {
    font-size: 31rpx;
    font-weight: 600;
    color: $ys-ink-1;
  }

  &__time {
    font-size: 25rpx;
    color: $ys-brand;
  }

  &__meta {
    display: flex;
    align-items: center;
    margin-top: 15rpx;
  }

  &__tag {
    padding: 4rpx 12rpx;
    font-size: 21rpx;
    line-height: 1.4;
    color: $ys-brand;
    background: $ys-brand-pale;
    border-radius: 8rpx;
  }

  &__dot {
    margin: 0 8rpx;
    font-size: 21rpx;
    color: $ys-ink-4;
  }

  &__meta-text {
    font-size: 23rpx;
    color: $ys-ink-3;
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 23rpx;
  }

  &__coach {
    display: flex;
    align-items: center;
    flex: 1;
    overflow: hidden;
  }

  &__avatar {
    width: 44rpx;
    height: 44rpx;
    border-radius: 50%;
    flex: none;
  }

  &__coach-name {
    margin-left: 10rpx;
    font-size: 25rpx;
    color: $ys-ink-2;
  }

  &__room {
    margin-left: 15rpx;
    font-size: 23rpx;
    color: $ys-ink-3;
  }

  &__action {
    display: flex;
    align-items: center;
    flex: none;
  }

  &__left {
    margin-right: 15rpx;
    font-size: 23rpx;
    color: $ys-ink-3;

    &--full {
      color: $ys-star;
    }
  }

  &__btn {
    padding: 10rpx 25rpx;
    font-size: 25rpx;
    color: #fff;
    background: $ys-brand;
    border-radius: 27rpx;

    &--disabled {
      background: #c9d3dd;
    }
  }
}
</style>
