<template>
  <view class="coaches-page">
    <view class="coaches-page__head">
      <text class="coaches-page__title">全部教练</text>
      <text class="coaches-page__desc">选择适合你的专业教练</text>
    </view>

    <view v-if="error && !coaches.length" class="state">
      <text class="state__text">教练数据加载失败</text>
      <view class="state__button press" @tap="reload">
        <text class="state__button-text">重新加载</text>
      </view>
    </view>

    <view v-else-if="!coaches.length && !loading" class="state">
      <text class="state__text">暂无教练</text>
    </view>

    <view v-else class="coach-list">
      <view v-for="coach in coaches" :key="coach.id" class="coach-card">
        <image
          class="coach-card__avatar"
          :src="coach.avatarUrl || defaultAvatar"
          mode="aspectFill"
        />
        <view class="coach-card__info">
          <text class="coach-card__name">{{ coach.name || '未命名教练' }}</text>
          <text class="coach-card__title">{{ coach.title || '专业教练' }}</text>
        </view>
        <text class="coach-card__arrow" decode>{{ '>' }}</text>
      </view>
    </view>

    <view v-if="loading" class="load-state">
      <text class="load-state__text">加载中...</text>
    </view>
    <view v-else-if="coaches.length && !hasMore" class="load-state">
      <text class="load-state__text">没有更多教练了</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { listCoaches } from '@/api/coach'

const PAGE_SIZE = 10
const defaultAvatar = '/static/images/home/coach1-avatar.jpg'

const coaches = ref([])
const pageNum = ref(0)
const total = ref(0)
const loading = ref(false)
const error = ref(false)

const hasMore = computed(() => coaches.value.length < total.value)

async function loadPage(reset = false) {
  if (loading.value) {
    return
  }
  if (!reset && pageNum.value > 0 && !hasMore.value) {
    return
  }

  loading.value = true
  error.value = false
  const nextPage = reset ? 1 : pageNum.value + 1
  try {
    const result = await listCoaches({ pageNum: nextPage, pageSize: PAGE_SIZE })
    coaches.value = reset ? result.list : coaches.value.concat(result.list)
    pageNum.value = nextPage
    total.value = result.total
  } catch (requestError) {
    error.value = true
  } finally {
    loading.value = false
  }
}

function reload() {
  loadPage(true)
}

onLoad(() => {
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
.coaches-page {
  min-height: 100vh;
  padding: 32rpx $ys-gap 48rpx;
  box-sizing: border-box;
  background: #f7f9fc;
}

.coaches-page__head {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  padding: 16rpx 8rpx 28rpx;
}

.coaches-page__title {
  font-size: 38rpx;
  font-weight: 600;
  line-height: 52rpx;
  color: $ys-title;
}

.coaches-page__desc {
  font-size: 25rpx;
  line-height: 36rpx;
  color: $ys-ink-3;
}

.coach-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.coach-card {
  display: flex;
  align-items: center;
  min-height: 156rpx;
  padding: 24rpx;
  box-sizing: border-box;
  background: #fff;
  border-radius: $ys-radius-md;
  box-shadow: $ys-card-shadow;
}

.coach-card__avatar {
  flex: none;
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: $ys-line;
}

.coach-card__info {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 14rpx;
  margin-left: 24rpx;
}

.coach-card__name {
  overflow: hidden;
  font-size: 31rpx;
  font-weight: 600;
  line-height: 44rpx;
  color: $ys-title;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.coach-card__title {
  overflow: hidden;
  font-size: 25rpx;
  line-height: 36rpx;
  color: $ys-ink-3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.coach-card__arrow {
  flex: none;
  margin-left: 16rpx;
  font-size: 34rpx;
  color: $ys-ink-3;
}

.state {
  display: flex;
  align-items: center;
  flex-direction: column;
  padding: 160rpx 0;
}

.state__text,
.load-state__text {
  font-size: 27rpx;
  color: $ys-ink-3;
}

.state__button {
  margin-top: 28rpx;
  padding: 14rpx 32rpx;
  border-radius: 999rpx;
  background: $ys-brand;
}

.state__button-text {
  font-size: 25rpx;
  color: #fff;
}

.load-state {
  display: flex;
  justify-content: center;
  padding: 32rpx 0 8rpx;
}
</style>
