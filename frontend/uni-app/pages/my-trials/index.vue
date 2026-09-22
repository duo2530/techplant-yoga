<template>
  <view class="trials">
    <!-- ===== 状态筛选 Tab 栏 =====
         实测（源图 我的体验课.png，1170×2532 @3x，逻辑 390×844）：
         白底 Tab 栏从导航栏下沿 y=91pt 到 y=132pt → 栏高 41pt（79rpx），下沿即 #F8F8F8 内容区；
         四个 Tab 文字中心 x = 48.75 / 146.25 / 243.75 / 341.25（等距，flex:1 居中即可）；
         文字墨迹 y 105.3..118pt（15pt，居中于 91..132）、选中下划线量到 x 38.7..58.3 / y 129..131.7pt
         → 20×3pt、圆角 1.5pt，贴栏底。
         ⚠️ 原型 HTML 给的是「栏高 54pt + 空态 padding-top 93pt」，两者相加=147pt；
            实测 41pt + 103pt = 144pt，只差 3pt。这里按实测拆分（Tab 栏高度以文字垂直居中为准）。 -->
    <view class="tabs">
      <view
        v-for="tab in tabs"
        :key="tab.value"
        class="tabs__item press"
        @tap="onTabChange(tab.value)"
      >
        <text class="tabs__text" :class="{ 'tabs__text--on': tab.value === activeStatus }">
          {{ tab.label }}
        </text>
        <view class="tabs__bar" :class="{ 'tabs__bar--on': tab.value === activeStatus }" />
      </view>
    </view>

    <!-- ===== 空态（默认，照原型）=====
         插画 /static/images/empty/trial.png（83×83pt，裁自原型截图 逻辑矩形 153,235,83,83）；
         与文案间距 13pt（ys-empty 内置 25rpx=13pt）；文案「暂无数据」#AAAAAA。
         整块 padding-top 103pt = 198rpx（Tab 栏下沿 41pt → 插画顶 144pt，与实测 235pt-91pt 一致）。 -->
    <view v-if="!list.length" class="empty">
      <ys-empty
        image="/static/images/empty/trial.png"
        image-width="160rpx"
        image-height="160rpx"
        title="暂无数据"
        :padding-top="198"
      />
    </view>

    <!-- ===== 有数据时（MOCK.empty.trials = false）=====
         ⚠️ 原型这一页是空态，**没有提供申请卡片样式**；下面这张白卡是按设计 token 推导的，
            原型补出卡片后改这一处即可。 -->
    <view v-else class="list">
      <view v-for="item in list" :key="item.id" class="card">
        <view class="card__head">
          <text class="card__store">{{ item.storeName }}</text>
          <text class="card__status" :class="'card__status--' + item.status">{{ item.statusLabel }}</text>
        </view>
        <text class="card__course">{{ item.courseName }}</text>
        <text class="card__time">期望时间　{{ item.expectDate }} {{ item.expectTime }}</text>
        <text class="card__apply">提交时间　{{ item.applyTime }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getTrialStatusTabs, listTrialApplications } from '@/api/trial'

/**
 * 我的体验课（照 docs/原型/my-trials.html）
 *
 * 原型截图里整页除「插画 + 暂无数据」外完全空白（复刻规范 §1.2：空态也要复刻，不塞假数据），
 * 默认空态由 mock/config.js 的 MOCK.empty.trials 控制；改成 false 会走下面的卡片分支。
 *
 * 数据全部来自 @/api/trial（内部读 mock），不发任何网络请求。
 * 状态栏 / 导航栏 / TabBar 由小程序绘制，本页不画（标题「我的体验课」已在 pages.json）。
 */

const tabs = ref([])
const list = ref([])
const activeStatus = ref(0)

async function loadList() {
  list.value = await listTrialApplications({ status: activeStatus.value })
}

async function init() {
  if (!tabs.value.length) {
    tabs.value = await getTrialStatusTabs()
  }
  await loadList()
}

// 用 onShow：从「申请体验」页提交成功返回时要刷新列表
onShow(() => {
  init()
})

function onTabChange(value) {
  if (value === activeStatus.value) {
    return
  }
  activeStatus.value = value
  loadList()
}
</script>

<style lang="scss" scoped>
.trials {
  min-height: 100vh;
  background: $ys-page-warm; /* 内容区底 #F8F8F8（实测） */
}

/* ---------------- 状态筛选 Tab 栏：白底 41pt ---------------- */
.tabs {
  display: flex;
  height: 79rpx; /* 41pt（实测；原型 HTML 写 54pt，见模板注释） */
  background: #fff;

  &__item {
    position: relative;
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
  }

  &__text {
    font-size: 29rpx; /* 15pt */
    line-height: 1;
    color: #080808; /* 实测未选中 #080808（token 里没有对应值） */

    &--on {
      color: $ys-brand-trial; /* #3165CC */
    }
  }

  /* 20×3pt 圆角下划线，贴 Tab 栏底；两态都占位，避免选中时文字跳位 */
  &__bar {
    position: absolute;
    left: 50%;
    bottom: 0;
    margin-left: -19rpx;
    width: 38rpx; /* 20pt */
    height: 6rpx; /* 3pt */
    border-radius: 3rpx;
    background: transparent;

    &--on {
      background: $ys-brand-trial;
    }
  }
}

/* ---------------- 空态 ---------------- */
.empty {
  background: $ys-page-warm;
}

/* ---------------- 申请卡片列表（原型未提供样式，按 token 推导）---------------- */
.list {
  /* 卡片间距用 flex + gap：WXSS 不支持星号通配符选择器（会报 error at token） */
  display: flex;
  flex-direction: column;
  gap: 19rpx;
  padding: 25rpx $ys-gap 0;
}

.card {
  padding: 25rpx;
  background: #fff;
  border-radius: $ys-radius-lg;

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__store {
    flex: 1;
    margin-right: 15rpx;
    font-size: 29rpx;
    font-weight: 600;
    line-height: 1;
    color: $ys-ink-1;
  }

  &__status {
    flex: none;
    font-size: 25rpx;
    line-height: 1;
    color: $ys-ink-3;

    /* 状态色：待审核=弱化灰、通过=主色、拒绝=警示红（都用现有 token，不发明新色值） */
    &--2 {
      color: $ys-brand;
    }

    &--3 {
      color: $ys-star;
    }
  }

  &__course {
    display: block;
    margin-top: 19rpx;
    font-size: 27rpx;
    line-height: 1;
    color: $ys-ink-1;
  }

  &__time,
  &__apply {
    display: block;
    margin-top: 12rpx;
    font-size: 25rpx;
    line-height: 1;
    color: $ys-ink-3;
  }
}
</style>
