<template>
  <!--
    约课页（W6）
    数据：GET /api/schedules（storeId ＋ courseType ＋ date ＋ 分页）。

    状态栏 / 导航栏 / 微信胶囊 / TabBar 由小程序绘制，本页不画。
    状态文案（可约 / 已结束 / 已取消）与是否可预约（bookable）**一律用后端返回值**，
    前端不做任何推导、也不额外过滤：可见口径是后端施加的 `status <> 1`，
    所以「已结束 / 已取消」的卡片照常出现在列表里，只是置灰、不可预约，仍可点进详情。
  -->
  <view class="booking">
    <!-- 顶部浅蓝渐隐带画在页面根节点 .booking 上（0 → 356rpx 由 #BAE6FC 渐隐到页面底色 #F7F7F7） -->
    <view class="booking__top">
      <!-- 当前门店：门店由首页「换店」决定（本地存储），这里点一下回首页切换 -->
      <view class="booking__store press" @tap="goHome">
        <uni-icons class="booking__store-icon" type="location" size="14" color="#3D7FC4" />
        <text class="booking__store-name">{{ storeName || '未选择门店' }}</text>
        <text class="booking__store-hint" decode>换店 {{ '>' }}</text>
      </view>

      <!-- 课种 Tab：团课 / 精品课 / 私教课 / 特色课（契约里的 courseType 1~4） -->
      <view class="tabs">
        <view
          v-for="type in courseTabs"
          :key="type.value"
          class="tabs__item press"
          @tap="onTypeChange(type.value)"
        >
          <view class="tabs__hit">
            <text class="tabs__text" :class="{ 'tabs__text--on': type.value === activeType }">
              {{ type.label }}
            </text>
            <view v-if="type.value === activeType" class="tabs__hi" />
          </view>
        </view>
      </view>
    </view>

    <!-- 日期条：今天起 14 天（BR-排课-017），默认选中今天；横向可滑动 -->
    <view class="date-card">
      <scroll-view class="date-card__scroll" scroll-x :show-scrollbar="false">
        <view class="day-strip">
          <view
            v-for="day in dates"
            :key="day.date"
            class="day-col press"
            @tap="onDateChange(day.date)"
          >
            <view class="day-pill" :class="day.date === activeDate ? 'day-pill--on' : 'day-pill--off'">
              <text class="day-pill__week">{{ day.weekShort }}</text>
              <text class="day-pill__date">{{ day.dateShort }}</text>
            </view>
            <view v-if="day.date === activeDate" class="day-col__check">
              <uni-icons type="checkmarkempty" size="11" color="#609CE9" />
            </view>
            <view v-else class="day-col__dot" />
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 场次列表：卡片九要素（封面 / 课程名 / 课种 / 难度 / 时间 / 教练 / 门店 / 教室 / 剩余名额 ＋ 状态） -->
    <view v-if="schedules.length" class="schedules">
      <view
        v-for="item in schedules"
        :key="item.id"
        class="ccard press"
        :class="{ 'ccard--off': !item.bookable }"
        @tap="goDetail(item)"
      >
        <image class="ccard__cover" :src="coverOf(item)" mode="aspectFill" />

        <view class="ccard__main">
          <view class="ccard__head">
            <text class="ccard__title">{{ item.courseName }}</text>
            <text class="ccard__status" :class="statusClass(item)">{{ item.statusText }}</text>
          </view>

          <view class="ccard__meta">
            <text class="ccard__tag">{{ item.courseTypeName }}</text>
            <text class="ccard__dot">·</text>
            <text class="ccard__meta-text">难度 {{ item.difficulty }}</text>
            <text class="ccard__dot">·</text>
            <text class="ccard__meta-text">{{ timeText(item) }}</text>
          </view>

          <text class="ccard__line">教练 {{ item.coachName || '待定' }}</text>
          <text class="ccard__line">{{ placeText(item) }}</text>

          <view class="ccard__foot">
            <text class="ccard__left">剩余 {{ item.remainingPlaces }} 个名额</text>
            <view
              class="ccard__btn"
              :class="{ 'ccard__btn--off': !item.bookable }"
              @tap.stop="onBook(item)"
            >
              <text>{{ item.bookable ? '立即预约' : '不可预约' }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="schedules__tip">
        <text v-if="loading">加载中…</text>
        <text v-else-if="finished">没有更多了</text>
      </view>
    </view>

    <!-- 空态：该门店该课种该日期没有数据（一期「私教课」固定为空） -->
    <view v-else-if="loaded" class="booking__empty">
      <ys-empty
        image="/static/images/empty/booking.png"
        image-width="265rpx"
        image-height="108rpx"
        title="当天暂无可约课程"
        subtitle="可切换课种或日期查看"
        :padding-top="60"
      />
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { listSchedules } from '@/api/schedule'
import { getCurrentStore } from '@/api/store'

/**
 * 约课页（W6）
 *
 * - 课种 Tab 四类（契约 §2.2 的 courseType 1~4）；卡片上的课种文案**一律用后端 courseTypeName**。
 *   Tab 的四个入口值是接口契约的一部分（用户端没有课种字典接口），
 *   文案优先用后端 courseTypeName 校准（syncTypeLabels），模板里不做码→名映射。
 * - 日期条今天起 14 天，默认今天；筛的是后端派生列 schedule_date。
 * - 切门店 / 切课种 / 切日期都重新查询并回到第 1 页。
 * - 状态文案与 bookable 全部来自后端（可约 / 已结束 / 已取消）；前端不过滤、不推导。
 */

const PAGE_SIZE = 10
/** 日期条天数（BR-排课-017：只能看今天起 14 天内的排课，含今天） */
const DATE_DAYS = 14
const WEEK_SHORT = ['日', '一', '二', '三', '四', '五', '六']

const courseTabs = ref([
  { value: 1, label: '团课' },
  { value: 2, label: '精品课' },
  { value: 3, label: '私教课' },
  { value: 4, label: '特色课' }
])

const dates = ref([])
/** 默认今天 */
const activeDate = ref('')
/** 默认第一个课种（团课） */
const activeType = ref(1)

const storeId = ref('')
const storeName = ref('')

const schedules = ref([])
const total = ref(0)
const page = ref(0)
const loading = ref(false)
const finished = ref(false)
const loaded = ref(false)

/* ---------------- 日期条 ---------------- */

function pad2(n) {
  return n < 10 ? '0' + n : '' + n
}

function formatDate(date) {
  return date.getFullYear() + '-' + pad2(date.getMonth() + 1) + '-' + pad2(date.getDate())
}

/** 今天起 14 天：日期条只展示，筛选一律走后端 schedule_date */
function buildDates() {
  const out = []
  const base = new Date()
  base.setHours(0, 0, 0, 0)
  for (let i = 0; i < DATE_DAYS; i++) {
    const day = new Date(base.getTime() + i * 24 * 60 * 60 * 1000)
    out.push({
      date: formatDate(day),
      weekShort: WEEK_SHORT[day.getDay()],
      dateShort: (day.getMonth() + 1) + '.' + day.getDate()
    })
  }
  return out
}

dates.value = buildDates()
activeDate.value = dates.value.length ? dates.value[0].date : ''

/* ---------------- 查询 ---------------- */

async function ensureStore() {
  const store = await getCurrentStore()
  if (store) {
    storeId.value = String(store.id)
    storeName.value = store.name || ''
  } else {
    storeId.value = ''
    storeName.value = ''
  }
}

async function loadPage(targetPage) {
  if (!storeId.value) {
    schedules.value = []
    total.value = 0
    finished.value = true
    loaded.value = true
    return
  }
  if (loading.value) {
    return
  }
  loading.value = true
  try {
    const res = await listSchedules({
      storeId: storeId.value,
      courseType: activeType.value,
      date: activeDate.value,
      pageNum: targetPage,
      pageSize: PAGE_SIZE
    })
    total.value = res.total
    page.value = targetPage
    schedules.value = targetPage === 1 ? res.list : schedules.value.concat(res.list)
    syncTypeLabels(res.list)
    finished.value = schedules.value.length >= total.value || res.list.length === 0
  } catch (error) {
    // 错误提示由 utils/request 统一 toast；查询失败按空列表兜底，避免旧数据串页
    if (targetPage === 1) {
      schedules.value = []
      total.value = 0
    }
    finished.value = true
  } finally {
    loading.value = false
    loaded.value = true
  }
}

/** 重新查询：任何筛选变化都回到第 1 页 */
function reload() {
  return loadPage(1)
}

/** 用后端返回的 courseTypeName 校准 Tab 文案（不回退到前端的码→名映射） */
function findTab(value) {
  for (let i = 0; i < courseTabs.value.length; i++) {
    if (Number(courseTabs.value[i].value) === Number(value)) {
      return courseTabs.value[i]
    }
  }
  return null
}

function syncTypeLabels(rows) {
  let changed = false
  rows.forEach((row) => {
    const tab = findTab(row.courseType)
    if (tab && row.courseTypeName && tab.label !== row.courseTypeName) {
      tab.label = row.courseTypeName
      changed = true
    }
  })
  if (changed) {
    // 触发一次视图更新（Tab 文案来自后端）
    courseTabs.value = courseTabs.value.slice()
  }
}

onShow(async () => {
  await ensureStore()
  await reload()
})

onReachBottom(() => {
  if (!finished.value && !loading.value && schedules.value.length) {
    loadPage(page.value + 1)
  }
})

function onTypeChange(value) {
  if (value === activeType.value) {
    return
  }
  activeType.value = value
  reload()
}

function onDateChange(date) {
  if (date === activeDate.value) {
    return
  }
  activeDate.value = date
  reload()
}

/* ---------------- 卡片展示 ---------------- */

/** 封面：后端未配图时用原型封面占位（不造课程数据，只兜底视觉） */
function coverOf(item) {
  return item.courseCoverUrl || '/static/images/booking/cover-1.jpg'
}

/** 起止时间只取时分（scheduleDate 已在日期条上体现） */
function hm(value) {
  const text = String(value || '')
  return text.length >= 16 ? text.slice(11, 16) : text
}

function timeText(item) {
  const start = hm(item.startTime)
  const end = hm(item.endTime)
  if (!start && !end) {
    return '时间待定'
  }
  return start + '-' + end
}

/** 门店 · 教室（对象被物理删除时后端给空字符串，这里跳过空的段） */
function placeText(item) {
  const parts = []
  if (item.storeName) {
    parts.push(item.storeName)
  }
  if (item.classroomName) {
    parts.push(item.classroomName)
  }
  return parts.length ? parts.join(' · ') : '门店 / 教室待定'
}

/** 状态徽标配色由后端文案决定（可约 = 蓝，已取消 = 灰红，其余 = 灰） */
function statusClass(item) {
  if (item.bookable) {
    return 'ccard__status--on'
  }
  return item.statusText === '已取消' ? 'ccard__status--cancel' : 'ccard__status--off'
}

/* ---------------- 交互 ---------------- */

/** 整卡可点（含已结束 / 已取消）→ 课程详情页 */
function goDetail(item) {
  uni.navigateTo({ url: '/pages/detail/index?scheduleId=' + String(item.id) })
}

/**
 * 「立即预约」：一期没有预约（BR-排课-018），只提示「敬请期待」，**不发任何请求**。
 * 已结束 / 已取消（bookable=false）不可预约，按钮置灰且不响应。
 */
function onBook(item) {
  if (!item.bookable) {
    return
  }
  uni.showToast({ title: '敬请期待', icon: 'none' })
}

function goHome() {
  uni.switchTab({ url: '/pages/home/index' })
}
</script>

<style lang="scss" scoped>
.booking {
  min-height: 100vh;
  /*
    顶部浅蓝渐隐带（原型 `.screen` 底色，按截图 x=30 竖向取色实测）：
      0..90pt 纯 #BAE6FC、90..275pt 渐隐到页面底色 #F7F7F7。
    小程序内容从系统导航栏以下开始，所以这里直接从 #BAE6FC 起、渐隐 185pt（=356rpx）。
    渐变必须画在页面根节点上，否则元素底边会硬切出一条蓝线。
  */
  background: linear-gradient(180deg, #bae6fc 0rpx, #f7f7f7 356rpx, #f7f7f7 100%);

  &__top {
    background: transparent;
  }

  /* 当前门店一行 */
  &__store {
    display: flex;
    align-items: center;
    padding: 20rpx $ys-gap 0;
  }

  &__store-icon {
    flex: none;
    line-height: 1;
  }

  &__store-name {
    flex: 1;
    margin-left: 10rpx;
    font-size: 27rpx;
    font-weight: 500;
    line-height: 1.3;
    color: #2c5163;
  }

  &__store-hint {
    flex: none;
    font-size: 23rpx;
    line-height: 1.3;
    color: $ys-brand-link;
  }

  &__empty {
    padding-top: 130rpx;
  }
}

/* ---------------- 品类 Tab ---------------- */
.tabs {
  display: flex;
  align-items: center;
  height: 67rpx;
  padding: 27rpx $ys-gap 0;

  &__item {
    flex: 1;
    display: flex;
    justify-content: center;
  }

  /*
    逐条对齐原型 booking.html:134-146（原型单位 pt，rpx = pt × 1.9231）：
      .tab-hit  { height:22px; line-height:22px }                  → 42rpx 高的 hit 框
      .tab-hi   { bottom:3px; width:47px; height:8px }             → bottom 6rpx / 高 15rpx
    选中文字压在蓝条上面（原型 z-10），字号 33rpx。
  */
  &__hit {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    height: 42rpx;
    line-height: 42rpx;
  }

  &__text {
    font-size: 33rpx;
    line-height: 1;
    color: #71777e;

    &--on {
      position: relative;
      z-index: 10;
      font-weight: 600;
      color: #303337;
    }
  }

  &__hi {
    position: absolute;
    left: 50%;
    bottom: 6rpx;
    width: 98rpx;
    height: 15rpx;
    margin-left: -49rpx;
    border-radius: 999px;
    background: #609ae9;
  }
}

/* ---------------- 日期条 ---------------- */
.date-card {
  margin: 38rpx 29rpx 0;
  height: 163rpx;
  background: #fff;
  border-radius: 19rpx;
  box-shadow: $ys-card-shadow;
  overflow: hidden;

  &__scroll {
    width: 100%;
    height: 163rpx;
    white-space: nowrap;
  }
}

.day-strip {
  display: inline-flex;
  align-items: flex-start;
  gap: 4rpx;
  padding: 5rpx 31rpx 0;
  height: 163rpx;
}

.day-col {
  position: relative;
  flex: none;
  width: 88rpx;
  height: 154rpx;

  &__check {
    position: absolute;
    left: 50%;
    top: 114rpx;
    width: 29rpx;
    height: 29rpx;
    margin-left: -15rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: #fff;
  }

  &__dot {
    position: absolute;
    left: 50%;
    top: 123rpx;
    width: 8rpx;
    height: 8rpx;
    margin-left: -4rpx;
    border-radius: 50%;
    background: $ys-brand;
  }
}

.day-pill {
  position: absolute;
  left: 0;
  top: 0;
  box-sizing: border-box;
  width: 88rpx;
  height: 146rpx;
  padding-top: 24rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  border-radius: 27rpx;

  &--on {
    background: $ys-brand;

    .day-pill__week,
    .day-pill__date {
      color: #fff;
    }
  }

  &--off {
    .day-pill__week,
    .day-pill__date {
      color: #303339;
    }
  }

  &__week {
    font-size: 31rpx;
    line-height: 32rpx;
  }

  &__date {
    margin-top: 15rpx;
    font-size: 31rpx;
    line-height: 32rpx;
  }
}

/* ---------------- 场次列表 ---------------- */
.schedules {
  /* 卡片间距用 flex + gap：WXSS 不支持通配符选择器（写成星号会被微信编译器拒绝） */
  display: flex;
  flex-direction: column;
  gap: 31rpx;
  padding: 44rpx 29rpx 38rpx;
}

.schedules__tip {
  padding: 25rpx 0 5rpx;
  text-align: center;
  font-size: 23rpx;
  color: $ys-ink-3;
}

/*
  场次卡：原型 .ccard 的外框实测值（卡片圆角 13pt、阴影 0 1px 2px rgba(0,0,0,.05)）
  按「卡片九要素」重排：封面 / 课程名 / 课种 / 难度 / 时间 / 教练 / 门店+教室 / 剩余名额 / 状态。
*/
.ccard {
  display: flex;
  padding: 27rpx 29rpx;
  background: #fff;
  border-radius: 25rpx;
  box-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.05);

  /* 已结束 / 已取消：整卡置灰（bookable = false），但仍可点进详情 */
  &--off {
    background: #f2f4f6;

    .ccard__cover {
      opacity: 0.5;
    }

    .ccard__title {
      color: $ys-ink-3;
    }
  }

  &__cover {
    flex: none;
    width: 163rpx;
    height: 163rpx;
    border-radius: 19rpx;
    background: $ys-line;
  }

  &__main {
    flex: 1;
    margin-left: 23rpx;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__title {
    flex: 1;
    font-size: 29rpx;
    font-weight: 600;
    line-height: 1.3;
    color: #303339;
  }

  &__status {
    flex: none;
    margin-left: 15rpx;
    padding: 4rpx 12rpx;
    font-size: 21rpx;
    line-height: 1.4;
    border-radius: 8rpx;

    &--on {
      color: $ys-brand;
      background: $ys-brand-pale;
    }

    &--off {
      color: $ys-ink-3;
      background: $ys-line;
    }

    &--cancel {
      color: #b25a5a;
      background: #f6e7e7;
    }
  }

  &__meta {
    display: flex;
    align-items: center;
    margin-top: 12rpx;
  }

  &__tag {
    padding: 2rpx 10rpx;
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

  &__line {
    margin-top: 8rpx;
    font-size: 23rpx;
    line-height: 1.3;
    color: $ys-ink-2;
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 16rpx;
  }

  &__left {
    flex: 1;
    font-size: 23rpx;
    color: $ys-ink-3;
  }

  &__btn {
    flex: none;
    padding: 10rpx 25rpx;
    font-size: 25rpx;
    line-height: 1;
    color: #fff;
    background: $ys-brand;
    border-radius: 56rpx;

    /* 已结束 / 已取消：不可预约，按钮置灰 */
    &--off {
      background: #c9d3dd;
    }
  }
}
</style>
