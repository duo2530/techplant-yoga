<template>
  <view class="act">
    <!-- 空态（原型整屏就是空态：浅蓝渐变底 + 灰色图标 + 「暂无数据」）
         图标 39×36pt、距内容顶 135pt；文案 15pt #AAAAAA、距图标 20pt -->
    <view v-if="!list.length" class="act__empty">
      <image class="act__icon" src="/static/images/empty/activity.png" mode="aspectFit" />
      <text class="act__tip">暂无数据</text>
    </view>

    <!-- 有数据时（把 mock/config.js 的 MOCK.empty.activities 改成 false 可看到）
         ⚠️ 原型没给活动卡片样式，下面是按设计 token 推导的；原型补出卡片后改这一处即可 -->
    <view v-else class="act__list">
      <view v-for="item in list" :key="item.id" class="card press" @tap="onOpen(item)">
        <image v-if="item.coverUrl" class="card__cover" :src="item.coverUrl" mode="aspectFill" />
        <view class="card__body">
          <view class="card__top">
            <text class="card__title">{{ item.title }}</text>
            <text class="card__status" :class="{ 'card__status--off': !item.status }">{{ item.statusLabel }}</text>
          </view>
          <text class="card__date">{{ item.startDate }} 至 {{ item.endDate }}</text>
          <text class="card__summary">{{ item.summary }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listActivities } from '@/api/activity'

/**
 * 活动列表（照 docs/原型/activities.html）
 *
 * 原型截图里**整屏是空态**：内容区竖向渐变 `linear-gradient(180deg,#E3F2FD,#8FC9F9)`，
 * 居中一个 39×36pt 的灰色描边图标（`#AAAAAA`）+ 15pt 的「暂无数据」。
 * 按 _复刻规范 §1.2「空态也要复刻，不要塞假数据」，默认就是空态
 * （由 `MOCK.empty.activities` 控制，见 mock/config.js）。
 *
 * 状态栏 / 导航栏 / 微信胶囊 / TabBar 由小程序绘制，本页不画（标题「活动列表」已在 pages.json）。
 */

const list = ref([])

onShow(async () => {
  list.value = await listActivities()
})

/** 原型里活动卡片没有跳转目标页（活动详情不在本轮原型范围内），只给点击反馈 */
function onOpen(item) {
  uni.showToast({ title: '活动详情页暂未提供', icon: 'none' })
}
</script>

<style lang="scss" scoped>
.act {
  min-height: 100vh;
  /* 原型实测：内容区竖向渐变 顶 #E3F2FD → 底 #8FC9F9 */
  background: linear-gradient(180deg, #e3f2fd 0%, #8fc9f9 100%);
}

.act__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 260rpx; /* 135pt */
}

.act__icon {
  width: 75rpx; /* 39pt */
  height: 69rpx; /* 36pt */
}

.act__tip {
  margin-top: 38rpx; /* 20pt */
  font-size: 29rpx; /* 15pt */
  line-height: 1;
  color: $ys-ink-muted;
}

.act__list {
  padding: 25rpx 25rpx 40rpx;
}

.card {
  display: flex;
  padding: 25rpx;
  background: #fff;
  border-radius: $ys-radius-lg;

  & + .card {
    margin-top: 23rpx;
  }

  &__cover {
    width: 160rpx;
    height: 160rpx;
    border-radius: $ys-radius-sm;
    flex: none;
  }

  &__body {
    flex: 1;
    margin-left: 23rpx;
    overflow: hidden;
  }

  &__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__title {
    font-size: 29rpx;
    font-weight: 600;
    color: $ys-ink-1;
  }

  &__status {
    flex: none;
    font-size: 23rpx;
    color: $ys-brand;

    &--off {
      color: $ys-ink-3;
    }
  }

  &__date {
    display: block;
    margin-top: 12rpx;
    font-size: 23rpx;
    color: $ys-ink-3;
  }

  &__summary {
    display: block;
    margin-top: 8rpx;
    font-size: 25rpx;
    color: $ys-ink-2;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
