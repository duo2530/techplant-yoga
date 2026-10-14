<template>
  <!--
    会员卡卡片（我的会员卡）

    按真实截图还原：次数卡用蓝色渐变，期限卡（年卡）用米金渐变；
    左上角分类角标、右上角卡号、卡名 + 状态胶囊、有效期、适用门店、底部数据区一应俱全。
    数据全部来自后端 MemberCardPublicItemVO，不在前端重新判定状态或推导次数。
  -->
  <view class="card" :class="isTimesCard ? 'card--times' : 'card--term'">
    <view class="card__star">
      <uni-icons type="star-filled" size="170" :color="starColor" />
    </view>

    <view class="card__chip">
      <text class="card__chip-text">{{ card.cardCategoryText }}</text>
    </view>
    <text class="card__no">{{ card.cardNo }}</text>

    <view class="card__head">
      <text class="card__name">{{ card.cardName }}</text>
      <view class="pill" :class="'pill--' + statusKey">
        <view class="pill__dot" />
        <text class="pill__text">{{ card.statusText }}</text>
      </view>
    </view>

    <text class="card__valid">{{ validText }}</text>

    <view class="card__store">
      <uni-icons type="location-filled" size="13" :color="subColor" />
      <text class="card__store-text">{{ card.storeScopeText }}</text>
    </view>

    <!-- 次数卡：剩余／初始次数 + 剩余进度条 -->
    <view v-if="isTimesCard" class="card__stats">
      <view class="stat">
        <text class="stat__label">当前剩余次数</text>
        <text class="stat__value">{{ remainText }}</text>
      </view>
      <view class="stat stat--right">
        <text class="stat__label">{{ initLabel }}</text>
        <text class="stat__value">{{ initText }}</text>
      </view>
    </view>
    <view v-if="isTimesCard" class="bar">
      <view class="bar__fill" :style="{ width: progressWidth }" />
    </view>

    <!-- 期限卡：剩余天数（含今天），未开卡或已过期时按空值提示 -->
    <view v-else class="card__stats">
      <view class="stat">
        <text class="stat__label">剩余天数</text>
        <text class="stat__value">{{ remainDaysText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

/**
 * 会员卡卡片
 *
 * 次数卡（cardType 1）与期限卡（cardType 2）两种版式；
 * 展示状态文案、总次数与年卡剩余天数都由后端算好，这里只做展示与空值兜底。
 */
const props = defineProps({
  card: { type: Object, required: true }
})

const isTimesCard = computed(() => props.card.cardType === 1)

/** 状态码 → 胶囊配色类名（后端返回 1~5） */
const statusKey = computed(() =>
  props.card.status === null || props.card.status === undefined ? '' : String(props.card.status)
)

const starColor = computed(() => (isTimesCard.value ? 'rgba(255, 255, 255, 0.16)' : 'rgba(255, 255, 255, 0.5)'))

const subColor = computed(() => (isTimesCard.value ? 'rgba(255, 255, 255, 0.88)' : 'rgba(110, 78, 43, 0.8)'))

function dateText(value) {
  return value ? String(value).substring(0, 10) : ''
}

/** 未开卡没有有效期止：照截图提示「开卡后开始计算有效期」 */
const validText = computed(() =>
  props.card.validEndTime ? '有效期至 ' + dateText(props.card.validEndTime) : '开卡后开始计算有效期'
)

const hasGift = computed(() => Number(props.card.giftTimes || 0) > 0)

const initLabel = computed(() => (hasGift.value ? '初始次数(包含赠送)' : '初始次数'))

/** 有赠送次数时照截图展示「15(1+14)次」 */
const initText = computed(() => {
  if (props.card.totalTimes === null) {
    return '暂无'
  }
  if (!hasGift.value) {
    return props.card.totalTimes + '次'
  }
  return props.card.totalTimes + '(' + props.card.purchaseTimes + '+' + props.card.giftTimes + ')次'
})

const remainText = computed(() =>
  props.card.remainingTimes === null ? '暂无' : props.card.remainingTimes + '次'
)

const progressWidth = computed(() => {
  const total = Number(props.card.totalTimes)
  const remain = Number(props.card.remainingTimes)
  if (!isFinite(total) || total <= 0 || !isFinite(remain)) {
    return '0%'
  }
  const ratio = Math.max(0, Math.min(1, remain / total))
  return (ratio * 100).toFixed(1) + '%'
})

/** 年卡剩余天数只在已开卡且未过期时有值，其余情况按空值提示 */
const remainDaysText = computed(() => {
  if (props.card.remainingDays !== null && props.card.remainingDays !== undefined) {
    return props.card.remainingDays + '天'
  }
  return props.card.validEndTime ? '已过期' : '暂无'
})
</script>

<style lang="scss" scoped>
.card {
  position: relative;
  box-sizing: border-box;
  width: 660rpx;
  padding: 26rpx 28rpx 28rpx;
  border-radius: 26rpx;
  overflow: hidden;

  &--times {
    background: linear-gradient(150deg, #93bef9 0%, #6c9be6 48%, #4070ce 100%);
    box-shadow: 0 8rpx 24rpx rgba(64, 112, 206, 0.2);
  }

  &--term {
    background: linear-gradient(150deg, #f3e0bc 0%, #ebd3a5 48%, #dcbe8b 100%);
    box-shadow: 0 8rpx 24rpx rgba(197, 160, 105, 0.22);
  }

  /* 右侧星形水印：贴卡片右下，压在文字下方 */
  &__star {
    position: absolute;
    right: 10rpx;
    bottom: -6rpx;
    line-height: 1;
    z-index: 0;
  }

  /* 左上角分类角标：左上取卡片圆角，右下单独收圆 */
  &__chip {
    position: absolute;
    left: 0;
    top: 0;
    z-index: 1;
    height: 46rpx;
    padding: 0 22rpx;
    border-radius: 26rpx 0 22rpx 0;
    display: flex;
    align-items: center;
  }

  &__chip-text {
    font-size: 23rpx;
    line-height: 1;
  }

  &__no {
    position: absolute;
    right: 28rpx;
    top: 26rpx;
    z-index: 1;
    font-size: 24rpx;
    line-height: 1;
    letter-spacing: 1rpx;
  }

  &__head {
    position: relative;
    z-index: 1;
    display: flex;
    align-items: center;
    margin-top: 44rpx;
  }

  &__name {
    flex: none;
    max-width: 400rpx;
    font-size: 38rpx;
    font-weight: 600;
    line-height: 1.1;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__valid,
  &__store {
    position: relative;
    z-index: 1;
  }

  &__valid {
    display: block;
    margin-top: 16rpx;
    font-size: 25rpx;
    line-height: 1.2;
  }

  &__store {
    display: flex;
    align-items: center;
    margin-top: 12rpx;
  }

  &__store-text {
    margin-left: 8rpx;
    font-size: 24rpx;
    line-height: 1.2;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__stats {
    position: relative;
    z-index: 1;
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    margin-top: 40rpx;
  }
}

/* 状态胶囊：两态都占位，底色随卡面半透明，圆点用状态色 */
.pill {
  flex: none;
  display: flex;
  align-items: center;
  height: 34rpx;
  margin-left: 16rpx;
  padding: 0 14rpx;
  border-radius: 17rpx;

  &__dot {
    flex: none;
    width: 10rpx;
    height: 10rpx;
    border-radius: 50%;
    margin-right: 8rpx;
  }

  &__text {
    font-size: 22rpx;
    line-height: 1;
  }

  &--1 .pill__dot {
    background: #f5a623;
  }

  &--2 .pill__dot {
    background: #2ecc71;
  }

  &--3 .pill__dot {
    background: #e74c3c;
  }

  &--4 .pill__dot {
    background: #9aa0a6;
  }

  &--5 .pill__dot {
    background: #f39c12;
  }
}

.stat {
  display: flex;
  flex-direction: column;

  &--right {
    align-items: flex-end;
  }

  &__label {
    font-size: 23rpx;
    line-height: 1;
  }

  &__value {
    margin-top: 12rpx;
    font-size: 34rpx;
    font-weight: 600;
    line-height: 1;
  }
}

.bar {
  position: relative;
  z-index: 1;
  height: 10rpx;
  margin-top: 20rpx;
  border-radius: 5rpx;
  overflow: hidden;

  &__fill {
    height: 100%;
    border-radius: 5rpx;
  }
}

/* ---------------- 卡面配色：文字、角标、胶囊、进度条 ---------------- */
.card--times {
  .card__chip {
    background: rgba(255, 255, 255, 0.22);
  }

  .card__chip-text {
    color: rgba(255, 255, 255, 0.95);
  }

  .card__no {
    color: rgba(255, 255, 255, 0.9);
  }

  .card__name {
    color: #ffffff;
  }

  .card__valid {
    color: rgba(255, 255, 255, 0.88);
  }

  .card__store-text {
    color: rgba(255, 255, 255, 0.88);
  }

  .pill {
    background: rgba(255, 255, 255, 0.26);
  }

  .pill__text {
    color: #ffffff;
  }

  .stat__label {
    color: rgba(255, 255, 255, 0.78);
  }

  .stat__value {
    color: #ffffff;
  }

  .bar {
    background: rgba(255, 255, 255, 0.32);
  }

  .bar__fill {
    background: #ffffff;
  }
}

.card--term {
  .card__chip {
    background: rgba(255, 255, 255, 0.42);
  }

  .card__chip-text {
    color: #7a5a33;
  }

  .card__no {
    color: rgba(110, 78, 43, 0.75);
  }

  .card__name {
    color: #6e4e2b;
  }

  .card__valid {
    color: rgba(110, 78, 43, 0.8);
  }

  .card__store-text {
    color: rgba(110, 78, 43, 0.8);
  }

  .pill {
    background: rgba(255, 255, 255, 0.55);
  }

  .pill__text {
    color: #6e4e2b;
  }

  .stat__label {
    color: rgba(110, 78, 43, 0.72);
  }

  .stat__value {
    color: #6e4e2b;
  }
}
</style>
