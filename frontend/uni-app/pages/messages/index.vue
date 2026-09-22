<template>
  <view class="msg">
    <!-- 空态（原型整页空态：插画 66×52pt + 「暂无数据」15pt/21pt #999999）
         原型注释：源图里插画之上还有一段留白，折算到内容区约 17pt -->
    <view v-if="!list.length" class="msg__empty">
      <image class="msg__pic" src="/static/images/empty/message.png" mode="aspectFit" />
      <text class="msg__tip">暂无数据</text>
    </view>

    <!-- 有数据时（MOCK.empty.messages = false）
         ⚠️ 原型没给消息卡片样式，下面是按设计 token 推导的；原型补出卡片后改这一处即可 -->
    <view v-else class="msg__list">
      <view v-for="item in list" :key="item.id" class="row press" @tap="onOpen(item)">
        <view class="row__head">
          <text class="row__title">{{ item.title }}</text>
          <text class="row__time">{{ item.time }}</text>
        </view>
        <text class="row__content">{{ item.content }}</text>
        <view v-if="!item.read" class="row__dot" />
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listMessages } from '@/api/message'

/**
 * 消息（照 docs/原型/messages.html）
 *
 * 原型截图里**整页是空态**：一张浅灰插画（山丘 + 云 + 飞碟 + 图片占位图标，66×52pt）
 * 加「暂无数据」15pt/21pt `#999999`，插画与文案间距 26pt。
 * 默认空态由 `MOCK.empty.messages` 控制（见 mock/config.js）。
 *
 * 注意：插画在小程序里不能内联 SVG，已由原型的 SVG 栅格化成
 * `/static/images/empty/message.png`（透明底）。
 *
 * 状态栏 / 导航栏 / TabBar 由小程序绘制，本页不画（标题「消息」已在 pages.json）。
 */

const list = ref([])

onShow(async () => {
  list.value = await listMessages()
})

/** 消息详情不在本轮原型范围内，只给点击反馈 */
function onOpen(item) {
  uni.showToast({ title: '消息详情页暂未提供', icon: 'none' })
}
</script>

<style lang="scss" scoped>
.msg {
  min-height: 100vh;
  background: #fff;
}

.msg__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 33rpx; /* 原型 .empty-wrap padding-top 17px */
}

.msg__pic {
  width: 127rpx; /* 66pt */
  height: 100rpx; /* 52pt */
}

.msg__tip {
  margin-top: 50rpx; /* 26pt */
  font-size: 29rpx; /* 15pt */
  line-height: 40rpx; /* 21pt */
  color: $ys-ink-3;
}

.msg__list {
  padding: 0 25rpx;
}

.row {
  position: relative;
  padding: 25rpx 0;
  border-bottom: 1px solid $ys-line;

  &__head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
  }

  &__title {
    font-size: 29rpx;
    font-weight: 600;
    color: $ys-ink-1;
  }

  &__time {
    flex: none;
    font-size: 23rpx;
    color: $ys-ink-3;
  }

  &__content {
    display: block;
    margin-top: 12rpx;
    font-size: 25rpx;
    line-height: 36rpx;
    color: $ys-ink-2;
  }

  &__dot {
    position: absolute;
    left: -12rpx;
    top: 34rpx;
    width: 12rpx;
    height: 12rpx;
    border-radius: 50%;
    background: $ys-star;
  }
}
</style>
