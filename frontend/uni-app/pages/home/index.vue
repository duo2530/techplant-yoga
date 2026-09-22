<template>
  <view class="home">
    <!-- 首页是 pages.json 里唯一的 navigationStyle:custom 页面，
         导航栏用现成组件还原「书法一 Logo + 品牌字左对齐」；
         状态栏高度由组件自己补，微信胶囊由系统绘制，这里不画。 -->
    <ys-nav-brand title="一水·瑜伽普拉提" />

    <!-- ================= 门店卡（头图 + 门店信息浮层） =================
         原型 home.html：照片 364×166pt、左右各 13pt、圆角 12pt、上边距 12.67pt。
         门店信息浮层压在照片上：左 8pt / 上 108pt / 212×50.67pt、内容左内边距 10pt。 -->
    <view class="store">
      <image class="store__hero" src="/static/images/home/store-hero.jpg" mode="aspectFill" />
      <view class="store__panel">
        <view class="store__line">
          <text class="store__name">{{ store.name || '' }}</text>
          <view class="store__change press" @tap="onChangeStore">
            <text class="store__change-text">换店 &gt;</text>
          </view>
        </view>
        <view class="store__line store__line--addr press" @tap="onOpenLocation">
          <uni-icons class="store__pin" type="map-pin" size="14" color="#5B7C8D" />
          <text class="store__addr">{{ store.address || '' }}</text>
        </view>
      </view>
    </view>

    <!-- ================= 公告栏（服务端驱动的展示区，非入口） ================= -->
    <view class="home__notice">
      <ys-notice-bar :text="noticeText" />
    </view>

    <!-- ================= 团课预约 / 私教预约（两列蓝色渐变卡，高 68pt） =================
         ⚠️ 原型里的渐变实测写作 `#4CBCEF → #82D0EF`（_复刻规范 §4 例外表），
         而 task 指定 `#0BABF3→#57C4E6` / `#22A5F4→#5CC3D8`（取自原型 HTML 的行内样式）。
         这里按 task 给的 HTML 行内值实现。 -->
    <view class="entry">
      <view class="entry__card entry__card--group press" @tap="goBookingTab">
        <image class="entry__icon" src="/static/images/home/icon-group.png" mode="aspectFit" />
        <view class="entry__text">
          <text class="entry__title">团课预约</text>
          <text class="entry__sub">GROUP CLASS</text>
        </view>
        <text class="entry__arrow">&gt;</text>
      </view>
      <view class="entry__card entry__card--private press" @tap="goBookingTab">
        <image class="entry__icon" src="/static/images/home/icon-private.png" mode="aspectFit" />
        <view class="entry__text">
          <text class="entry__title">私教预约</text>
          <text class="entry__sub">PRIVATE CLASS</text>
        </view>
        <text class="entry__arrow">&gt;</text>
      </view>
    </view>

    <!-- ================= 体验课 / 我要打卡 / 活动专区（三列白卡，高 41.67pt） ================= -->
    <view class="tools">
      <view class="tools__card press" @tap="goTrialApply">
        <image class="tools__icon" src="/static/images/home/icon-trial.png" mode="aspectFit" />
        <text class="tools__label">体验课</text>
        <text class="tools__arrow">&gt;</text>
      </view>
      <view class="tools__card press" @tap="goSharePoster">
        <image class="tools__icon" src="/static/images/home/icon-checkin.png" mode="aspectFit" />
        <text class="tools__label">我要打卡</text>
        <text class="tools__arrow">&gt;</text>
      </view>
      <view class="tools__card press" @tap="goActivities">
        <image class="tools__icon" src="/static/images/home/icon-activity.png" mode="aspectFit" />
        <text class="tools__label">活动专区</text>
        <text class="tools__arrow">&gt;</text>
      </view>
    </view>

    <!-- ================= 今日可约团课 =================
         原型是空态：白底 + 1px 边框（#E9EEF1）的圆角框 + 居中提示，框高 44pt。 -->
    <view class="home__head">
      <ys-section-head title="今日可约团课" more-text="查看全部" @more="goBookingTab" />
    </view>
    <view v-if="!todaySchedules.length" class="today-empty">
      <text class="today-empty__text">今日暂无可约课程</text>
    </view>
    <view v-else class="home__list">
      <ys-course-card
        v-for="item in todaySchedules"
        :key="item.id"
        :item="item"
        @tap="onCourseTap"
        @book="onBook"
      />
    </view>

    <!-- ================= 热门课程（原型空态：插画 + 文字，无边框框体） ================= -->
    <view class="home__head home__head--gap">
      <ys-section-head title="热门课程" more-text="更多课程" @more="goBookingTab" />
    </view>
    <view v-if="!hotCourses.length" class="hot-empty">
      <image class="hot-empty__img" src="/static/images/home/empty-hot-course.png" mode="aspectFit" />
      <text class="hot-empty__text">暂无热门课程</text>
    </view>
    <view v-else class="home__list">
      <ys-course-card
        v-for="item in hotCourses"
        :key="item.id"
        :item="hotCourseCard(item)"
        @tap="goBookingTab"
        @book="goBookingTab"
      />
    </view>

    <!-- ================= 金牌教练（三张教练卡） =================
         卡高 108.67pt、间隔 9.34pt；头像 79×79pt 圆角 8pt，右侧照片 143.33×108.67pt 贴右。 -->
    <view class="home__head home__head--coach">
      <ys-section-head title="金牌教练" more-text="更多教练" @more="goCoaches" />
    </view>
    <view class="coaches">
      <view
        v-for="coach in coaches"
        :key="coach.id"
        class="coach press"
        @tap="goCoaches"
      >
        <view class="coach__main">
          <image class="coach__avatar" :src="coachAvatar(coach)" mode="aspectFill" />
          <view class="coach__text">
            <text class="coach__name">{{ coach.name }}</text>
            <text class="coach__intro">暂无介绍</text>
            <text class="coach__link">查看课程 &gt;</text>
          </view>
        </view>
        <image class="coach__photo" :src="coachPhoto(coach)" mode="aspectFill" />
      </view>
    </view>

    <!-- ================= 场景横幅（无标题、无叠加 UI，纯图片 364×257pt） ================= -->
    <image class="scene" src="/static/images/home/banner-scene.jpg" mode="aspectFill" />

    <!-- ================= 门店实景图区（三张实景照片，无标题文字） ================= -->
    <image class="gallery" src="/static/images/store/photo-1.jpg" mode="aspectFill" />
    <image class="gallery gallery--2" src="/static/images/store/photo-2.jpg" mode="aspectFill" />
    <image class="gallery gallery--3" src="/static/images/store/photo-3.jpg" mode="aspectFill" />

    <!-- 内容区末尾留白（原型 h-[8px]），再往下是小程序自己绘制的 TabBar -->
    <view class="home__bottom" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getHomeData } from '@/api/home'
import { listStores, setCurrentStoreId } from '@/api/store'
import { bookSchedule } from '@/api/course'

/**
 * 首页（严格照 docs/原型/home.html）
 *
 * 数据来自 @/api/home（内部读 mock），不发任何网络请求。
 * 状态栏 / 导航栏 / 微信胶囊 / TabBar 都由小程序绘制，本页只画内容区：
 * 唯一例外是导航栏用现成组件 ys-nav-brand（pages.json 里首页是 navigationStyle:custom）。
 */

const home = ref({
  store: {},
  notices: [],
  todaySchedules: [],
  hotCourses: [],
  coaches: []
})

const noticeIndex = ref(0)

/** 公告栏：有多条时每 4 秒轮播；无公告时空文案 → 组件显示「暂无公告」 */
const noticeText = computed(() => {
  const list = home.value.notices || []
  if (!list.length) {
    return ''
  }
  return list[noticeIndex.value % list.length].content || ''
})

let noticeTimer = null

function startNoticeTimer() {
  stopNoticeTimer()
  if ((home.value.notices || []).length > 1) {
    noticeTimer = setInterval(() => {
      noticeIndex.value = (noticeIndex.value + 1) % home.value.notices.length
    }, 4000)
  }
}

function stopNoticeTimer() {
  if (noticeTimer) {
    clearInterval(noticeTimer)
    noticeTimer = null
  }
}

async function loadHome() {
  home.value = await getHomeData()
  noticeIndex.value = 0
  startNoticeTimer()
}

onShow(() => {
  loadHome()
})

/**
 * 「换店」：原型里跳的是登录页，按 task 要求改为弹出门店选择。
 * 选中后写回当前门店并重新拉数据。
 */
async function onChangeStore() {
  const stores = await listStores()
  if (!stores.length) {
    return
  }
  uni.showActionSheet({
    itemList: stores.map((item) => item.name),
    success: async (res) => {
      const picked = stores[res.tapIndex]
      if (!picked) {
        return
      }
      await setCurrentStoreId(picked.id)
      await loadHome()
    }
  })
}

/**
 * 定位针那一行：原型跳的是微信内置地图页（native-map.html）。
 * 小程序里打开地图要真实经纬度，而 mock/store.js 只给了门店地址文本，
 * 没有 lat/lng，所以**不臆造坐标**，只提示一下。
 * TODO 门店数据补上 latitude / longitude 后，改成 uni.openLocation({...})
 */
function onOpenLocation() {
  uni.showToast({ title: '地图功能暂未提供', icon: 'none' })
}

/* ---------------- 跳转（全部用 uni 原生 API，路径取自 pages.json） ---------------- */

function goBookingTab() {
  uni.switchTab({ url: '/pages/booking/index' })
}

function goMyBookingsTab() {
  uni.switchTab({ url: '/pages/my-bookings/index' })
}

function goTrialApply() {
  uni.navigateTo({ url: '/pages/trial-apply/index' })
}

/** 原型「我要打卡」跳的就是分享海报页 */
function goSharePoster() {
  uni.navigateTo({ url: '/pages/share-poster/index' })
}

function goActivities() {
  uni.navigateTo({ url: '/pages/activities/index' })
}

function goCoaches() {
  uni.navigateTo({ url: '/pages/coaches/index' })
}

function onCourseTap() {
  // TODO 原型没有课程详情页，暂不跳转；详情页登记进 pages.json 后再补
}

async function onBook(item) {
  const res = await bookSchedule(item.id)
  if (res && res.success) {
    uni.showToast({ title: '预约成功' })
    goMyBookingsTab()
    return
  }
  uni.showToast({ title: (res && res.message) || '预约失败', icon: 'none' })
}

/* ---------------- 展示兜底（mock 里教练头像/照片已配好，缺失时给占位） ---------------- */

function coachAvatar(coach) {
  return coach.avatar || '/static/images/home/coach1-avatar.jpg'
}

function coachPhoto(coach) {
  return coach.photo || '/static/images/home/coach1-photo.jpg'
}

/** 热门课程返回的是课程对象（不是场次），补成 ys-course-card 需要的形状 */
function hotCourseCard(course) {
  return {
    id: course.id,
    courseId: course.id,
    courseName: course.name,
    courseType: course.type,
    difficulty: course.difficulty,
    durationMin: course.durationMin,
    tag: (course.tags && course.tags[0]) || '',
    coachName: course.coachName || '',
    coachAvatar: '',
    room: '',
    left: 0,
    capacity: 0,
    soldOut: true
  }
}
</script>

<style lang="scss" scoped>
/* 页面底色：原型内容区是白色（不是 $ys-page 灰底） */
.home {
  min-height: 100vh;
  background: #fff;
}

/* ---------------- 门店卡 ---------------- */
.store {
  position: relative;
  margin: 24rpx $ys-gap 0;
  height: 319rpx;
  border-radius: $ys-radius-lg;
  overflow: hidden;
  box-shadow: $ys-card-shadow;

  &__hero {
    display: block;
    width: 100%;
    height: 319rpx;
  }

  /* 门店信息浮层：压住照片里烤进去的那张卡（位置与烤入的完全重合） */
  &__panel {
    position: absolute;
    left: 15rpx;
    top: 208rpx;
    box-sizing: border-box;
    width: 408rpx;
    height: 97rpx;
    padding: 0 15rpx 0 23rpx;
    display: flex;
    flex-direction: column;
    justify-content: center;
    border-radius: $ys-radius-md;
    background: rgba(226, 224, 221, 0.92);
  }

  &__line {
    display: flex;
    align-items: center;

    &--addr {
      margin-top: 15rpx;
    }
  }

  &__name {
    flex: none;
    font-size: 25rpx;
    line-height: 1;
    letter-spacing: -0.4rpx;
    white-space: nowrap;
    color: #203f4d;
  }

  &__change {
    margin-left: 6rpx;
    flex: none;
  }

  &__change-text {
    font-size: 19rpx;
    line-height: 1;
    white-space: nowrap;
    color: $ys-brand-link;
  }

  &__pin {
    flex: none;
    line-height: 1;
  }

  &__addr {
    margin-left: 8rpx;
    font-size: 20rpx;
    line-height: 1;
    white-space: nowrap;
    color: $ys-text-sub;
  }
}

/* ---------------- 公告栏 ---------------- */
.home__notice {
  margin: 19rpx $ys-gap 0;
}

/* ---------------- 团课 / 私教预约 ---------------- */
.entry {
  display: flex;
  margin: 24rpx $ys-gap 0;

  &__card {
    position: relative;
    flex: 1;
    box-sizing: border-box;
    height: 131rpx;
    padding: 0 27rpx 0 19rpx;
    display: flex;
    align-items: center;
    border-radius: $ys-radius-lg;

    &--group {
      background: linear-gradient(135deg, #0babf3 0%, #57c4e6 100%);
    }

    &--private {
      margin-left: 15rpx;
      background: linear-gradient(135deg, #22a5f4 0%, #5cc3d8 100%);
    }
  }

  &__icon {
    width: 58rpx;
    height: 58rpx;
    flex: none;
  }

  &__text {
    margin-left: 18rpx;
    display: flex;
    flex-direction: column;
  }

  &__title {
    font-size: 25rpx;
    font-weight: 600;
    line-height: 1;
    color: #fff;
  }

  &__sub {
    margin-top: 10rpx;
    font-size: 19rpx;
    line-height: 1;
    color: rgba(255, 255, 255, 0.85);
  }

  &__arrow {
    position: absolute;
    right: 27rpx;
    top: 50rpx;
    font-size: 25rpx;
    line-height: 1;
    color: rgba(255, 255, 255, 0.9);
  }
}

/* ---------------- 体验课 / 我要打卡 / 活动专区 ---------------- */
.tools {
  display: flex;
  margin: 33rpx $ys-gap 0;

  &__card {
    position: relative;
    flex: 1;
    box-sizing: border-box;
    height: 80rpx;
    padding: 0 19rpx 0 17rpx;
    display: flex;
    align-items: center;
    background: #fff;
    border-radius: $ys-radius-md;
    box-shadow: $ys-card-shadow;

    & + & {
      margin-left: 15rpx;
    }
  }

  &__icon {
    width: 42rpx;
    height: 42rpx;
    flex: none;
  }

  &__label {
    margin-left: 17rpx;
    font-size: 21rpx;
    line-height: 1;
    color: $ys-text-card;
  }

  &__arrow {
    position: absolute;
    right: 19rpx;
    font-size: 21rpx;
    line-height: 1;
    color: #9ca3af;
  }
}

/* ---------------- 区块标题 ---------------- */
.home__head {
  margin: 63rpx $ys-gap 0;

  &--gap {
    margin-top: 80rpx;
  }

  &--coach {
    margin-top: 191rpx;
  }
}

/* ---------------- 今日可约团课（空态框） ---------------- */
.today-empty {
  margin: 35rpx $ys-gap 0;
  height: 85rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1px solid #e9eef1;
  border-radius: $ys-radius-sm;

  &__text {
    font-size: 23rpx;
    line-height: 1;
    color: $ys-text-sub;
  }
}

/* ---------------- 热门课程（空态插画） ---------------- */
.hot-empty {
  margin: 117rpx $ys-gap 0;
  display: flex;
  align-items: center;
  justify-content: center;

  &__img {
    width: 108rpx;
    height: 58rpx;
    flex: none;
  }

  &__text {
    margin-left: 25rpx;
    font-size: 23rpx;
    line-height: 1;
    color: $ys-text-sub;
  }
}

/* ---------------- 有数据时的课程卡列表 ---------------- */
.home__list {
  /* 卡片间距用 flex + gap：WXSS 不支持星号通配符选择器（会报 error at token） */
  display: flex;
  flex-direction: column;
  gap: 15rpx;
  margin: 35rpx $ys-gap 0;
}

/* ---------------- 金牌教练 ---------------- */
.coaches {
  margin: 24rpx $ys-gap 0;
}

.coach {
  position: relative;
  height: 209rpx;
  margin-top: 18rpx;
  border-radius: $ys-radius-md;
  background: #fff;
  box-shadow: $ys-card-shadow;
  overflow: hidden;

  &:first-child {
    margin-top: 0;
  }

  &__main {
    position: relative;
    /* 右侧 276rpx 留给照片，文字区不会被照片压住 */
    padding: 0 276rpx 0 22rpx;
    height: 100%;
    display: flex;
    align-items: center;
  }

  &__avatar {
    width: 152rpx;
    height: 152rpx;
    flex: none;
    border-radius: $ys-radius-sm;
  }

  &__text {
    margin-left: 25rpx;
    display: flex;
    flex-direction: column;
  }

  &__name {
    font-size: 27rpx;
    font-weight: 600;
    line-height: 1;
    color: $ys-title;
  }

  &__intro {
    margin-top: 18rpx;
    font-size: 19rpx;
    line-height: 1;
    color: $ys-ink-3;
  }

  &__link {
    margin-top: 15rpx;
    font-size: 19rpx;
    line-height: 1;
    color: $ys-brand-link;
  }

  &__photo {
    position: absolute;
    right: 0;
    top: 0;
    width: 276rpx;
    height: 209rpx;
  }
}

/* ---------------- 场景横幅 / 门店实景 ---------------- */
.scene {
  display: block;
  margin: 31rpx $ys-gap 0;
  width: 700rpx;
  height: 494rpx;
  border-radius: $ys-radius-lg;
  box-shadow: $ys-card-shadow;
}

.gallery {
  display: block;
  margin: 53rpx $ys-gap 0;
  width: 700rpx;
  height: 467rpx;
  border-radius: $ys-radius-lg;
  box-shadow: $ys-card-shadow;

  &--2 {
    margin-top: 17rpx;
  }

  &--3 {
    margin-top: 17rpx;
    height: 300rpx;
  }
}

.home__bottom {
  height: 15rpx;
}
</style>
