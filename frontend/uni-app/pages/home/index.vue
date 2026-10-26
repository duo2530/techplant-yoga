<template>
  <view class="home">
    <!-- 首页是 pages.json 里唯一的 navigationStyle:custom 页面，
         导航栏用现成组件还原「书法一 Logo + 品牌字左对齐」；状态栏高度由组件自己补。 -->
    <ys-nav-brand title="瑜伽普拉提" />

    <!-- ================= 门店卡（W5 收敛后的唯一内容） =================
         展示当前门店的 name / regionName / address（有配图则带上 imageUrl）。
         门店数据来自 GET /api/stores；当前门店由小程序本地存储记住（BR-全局-010），
         不落服务端、不登录 —— 旧原型「换店要先登录」的拦截已删除。 -->
    <view v-if="hasStore" class="store">
      <image class="store__hero" :src="storeImage" mode="aspectFill" />
      <view class="store__body">
        <view class="store__row">
          <text class="store__name">{{ store.name }}</text>
          <view class="store__change press" @tap="openPicker">
            <text class="store__change-text" decode>换店 {{ '>' }}</text>
          </view>
        </view>

        <view v-if="store.regionName" class="store__meta">
          <uni-icons class="store__icon" type="location" size="13" color="#5B7C8D" />
          <text class="store__region">{{ store.regionName }}</text>
        </view>

        <view v-if="store.address" class="store__meta store__meta--addr">
          <uni-icons class="store__icon" type="map-pin" size="13" color="#5B7C8D" />
          <text class="store__addr">{{ store.address }}</text>
        </view>
      </view>
    </view>

    <!-- 无门店 → 空态（BR-用户端-003 的兜底） -->
    <view v-else-if="loaded" class="home__empty">
      <ys-empty title="暂无可选门店" subtitle="请稍后重新进入" padding-top="120" />
    </view>

    <!-- ================= 今日可约团课 =================
         数据来自 GET /api/schedules/today-bookable：当前门店今天未开始且有名额的团课，后端最多返回 2 条。
         没有当前门店时不请求，直接显示空态；加载与失败状态只影响本栏目。 -->
    <view v-if="hasStore" class="home-today">
      <view class="home-today__head">
        <ys-section-head title="今日可约团课" more-text="查看全部" @more="openBooking" />
      </view>

      <view v-if="todayLoading && !todayList.length" class="home-today__state">
        <text class="home-today__state-text">加载中…</text>
      </view>

      <view v-else-if="todayError && !todayList.length" class="home-today__state">
        <text class="home-today__state-text">今日可约团课加载失败</text>
        <view class="home-today__retry press" @tap="loadToday">
          <text class="home-today__retry-text">重试</text>
        </view>
      </view>

      <view v-else-if="!todayList.length" class="home-today__empty">
        <text class="home-today__state-text">今日暂无可约课程</text>
      </view>

      <view v-else class="home-today__list">
        <view v-for="item in todayList" :key="item.id" class="today-item">
          <view class="today-item__time">
            <text class="today-item__clock">{{ clockOf(item.startTime) }}</text>
            <text class="today-item__label">开课</text>
          </view>
          <view class="today-item__main">
            <text class="today-item__name">{{ item.courseName }}</text>
            <view class="today-item__meta">
              <view class="today-item__cell">
                <uni-icons type="person" size="14" color="#4799EF" />
                <text class="today-item__text">{{ item.coachName }}</text>
              </view>
              <view class="today-item__cell">
                <uni-icons type="location" size="14" color="#4799EF" />
                <text class="today-item__text">{{ item.classroomName }}</text>
              </view>
              <view class="today-item__cell">
                <uni-icons type="loop" size="14" color="#4799EF" />
                <text class="today-item__text">{{ durationOf(item) }}分钟</text>
              </view>
              <view class="today-item__cell">
                <uni-icons type="staff" size="14" color="#4799EF" />
                <text class="today-item__text">{{ item.bookedPersons }}/{{ item.maxPersons }}人</text>
              </view>
            </view>
          </view>
          <view class="today-item__go press" @tap="openSchedule(item)">
            <text class="today-item__go-text">去预约</text>
          </view>
        </view>
      </view>
    </view>

    <!-- ================= 金牌教练 =================
         平台级数据来自 GET /api/coaches/featured：一次展示全部金牌，不截断、不设「更多教练」入口。
         加载／空／失败状态与门店卡、换店互相独立；没有可选门店时列表照常展示。 -->
    <view class="home-coaches">
      <view class="home-coaches__head">
        <ys-section-head title="金牌教练" />
      </view>

      <view v-if="coachLoading && !coaches.length" class="home-coaches__state">
        <text class="home-coaches__state-text">加载中…</text>
      </view>

      <view v-else-if="coachError && !coaches.length" class="home-coaches__state">
        <text class="home-coaches__state-text">金牌教练加载失败</text>
        <view class="home-coaches__retry press" @tap="loadCoaches">
          <text class="home-coaches__retry-text">重试</text>
        </view>
      </view>

      <view v-else-if="!coaches.length" class="home-coaches__state">
        <text class="home-coaches__state-text">暂无金牌教练</text>
      </view>

      <view v-else class="home-coaches__list">
        <view v-for="coach in coaches" :key="coach.id" class="home-coach press" @tap="openCoach(coach)">
          <image
            v-if="coach.avatarUrl && !avatarFailed[coach.id]"
            class="home-coach__avatar"
            :src="coach.avatarUrl"
            mode="aspectFill"
            @error="onAvatarError(coach.id)"
          />
          <view v-else class="home-coach__avatar home-coach__avatar--empty">
            <uni-icons type="person-filled" size="26" color="#ADBAC4" />
          </view>
          <view class="home-coach__main">
            <text class="home-coach__name">{{ coach.name }}</text>
            <text class="home-coach__intro">{{ coach.intro || '暂无介绍' }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- ================= 换店：底部弹层（门店列表 / 区域筛选 / 关键字搜索 / 分页加载） ============== -->
    <view v-if="pickerVisible" class="picker">
      <view class="picker__mask" @tap="closePicker" />
      <view class="picker__panel">
        <view class="picker__head">
          <text class="picker__title">选择门店</text>
          <text class="picker__close press" @tap="closePicker">关闭</text>
        </view>

        <view class="picker__search">
          <uni-icons class="picker__search-icon" type="search" size="15" color="#999999" />
          <input
            class="picker__input"
            v-model="keyword"
            type="text"
            placeholder="搜索门店名称或地址"
            confirm-type="search"
            @confirm="onKeywordConfirm"
          />
          <text v-if="keyword" class="picker__clear press" @tap="onClearKeyword">清空</text>
        </view>

        <!-- 区域筛选：区域码来自 GET /api/stores 返回的 regionCode / regionName（用户端没有区域字典接口） -->
        <scroll-view v-if="regions.length" class="picker__regions" scroll-x :show-scrollbar="false">
          <view class="region-strip">
            <view
              class="region-chip press"
              :class="{ 'region-chip--on': activeRegion === '' }"
              @tap="onRegionTap('')"
            >
              <text class="region-chip__text">全部</text>
            </view>
            <view
              v-for="region in regions"
              :key="region.code"
              class="region-chip press"
              :class="{ 'region-chip--on': activeRegion === region.code }"
              @tap="onRegionTap(region.code)"
            >
              <text class="region-chip__text">{{ region.name }}</text>
            </view>
          </view>
        </scroll-view>

        <scroll-view
          class="picker__list"
          scroll-y
          :show-scrollbar="false"
          @scrolltolower="onPickerScrollLower"
        >
          <view v-if="pickerEmpty" class="picker__state">
            <ys-empty title="没有找到门店" padding-top="80" />
          </view>
          <view v-else class="picker__items">
            <view
              v-for="item in pickerList"
              :key="item.id"
              class="pitem press"
              @tap="pickStore(item)"
            >
              <view class="pitem__main">
                <text class="pitem__name">{{ item.name }}</text>
                <text v-if="item.regionName" class="pitem__sub">{{ item.regionName }}</text>
                <text v-if="item.address" class="pitem__addr">{{ item.address }}</text>
              </view>
              <uni-icons v-if="isCurrent(item)" type="checkmarkempty" size="18" color="#609CE9" />
            </view>
            <view class="picker__tip">
              <text v-if="pickerLoading">加载中…</text>
              <text v-else-if="pickerFinished">没有更多了</text>
              <text v-else>上拉加载更多</text>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- 内容区末尾留白，再往下是小程序自己绘制的 TabBar -->
    <view class="home__bottom" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listStores, getCurrentStoreId, getCachedStore, saveCurrentStore } from '@/api/store'
import { getFeaturedCoaches } from '@/api/coach'
import { listTodayBookable } from '@/api/schedule'

/** 首页（W5） */

/** 换店弹层每页条数（接口 pageSize 上限 100） */
const PICKER_PAGE_SIZE = 10

const store = ref({})
/** 是否已完成首次加载（用它区分「加载中」与「真的没有门店」） */
const loaded = ref(false)

/* ---------------- 今日可约团课状态 ---------------- */
const todayList = ref([])
const todayLoading = ref(false)
const todayError = ref(false)
/** 请求序号：换店或回到首页触发的新请求覆盖旧响应 */
let todayRequestSeq = 0

/* ---------------- 金牌教练状态 ---------------- */
const coaches = ref([])
const coachLoading = ref(true)
const coachError = ref(false)
/** 头像加载失败的教练 id；失败后换中性占位，避免反复请求坏图 */
const avatarFailed = ref({})
/** 请求序号：后发起的刷新覆盖先发起的结果，避免慢响应回写旧列表 */
let coachRequestSeq = 0

/* ---------------- 换店弹层状态 ---------------- */
const pickerVisible = ref(false)
const keyword = ref('')
const activeRegion = ref('')
const regions = ref([])
const pickerList = ref([])
const pickerPage = ref(1)
const pickerTotal = ref(0)
const pickerLoading = ref(false)
const pickerFinished = ref(false)
const pickerEmpty = ref(false)

const hasStore = computed(() => !!store.value.name)

/**
 * 门店头图：有 imageUrl 用后端的，没配图时用原型里的门店头图占位（不是造数据，只是兜底视觉）
 */
const storeImage = computed(() => store.value.imageUrl || '/static/images/home/store-hero.jpg')

/** 加载当前门店 */
async function loadCurrentStore() {
  const cached = getCachedStore()
  if (cached) {
    store.value = cached
  }
  try {
    const list = await listStores({ pageNum: 1, pageSize: 100 })
    if (!list.length) {
      store.value = {}
      return
    }
    const id = getCurrentStoreId()
    let hit = null
    for (let i = 0; i < list.length; i++) {
      if (String(list[i].id) === id) {
        hit = list[i]
        break
      }
    }
    store.value = saveCurrentStore(hit || list[0]) || hit || list[0]
  } catch (error) {
    // 请求失败：错误提示已由 utils/request 统一 toast；这里保留本地缓存不打断页面
  } finally {
    loaded.value = true
  }
}

/** 加载当前门店的今日可约团课；没有当前门店时清空并不请求 */
async function loadToday() {
  const seq = ++todayRequestSeq
  const storeId = store.value && store.value.id ? String(store.value.id) : ''
  if (!storeId) {
    todayList.value = []
    todayError.value = false
    todayLoading.value = false
    return
  }
  todayLoading.value = true
  todayError.value = false
  try {
    const list = await listTodayBookable(storeId)
    if (seq !== todayRequestSeq) {
      return
    }
    todayList.value = list
  } catch (error) {
    if (seq !== todayRequestSeq) {
      return
    }
    todayError.value = true
    todayList.value = []
  } finally {
    if (seq === todayRequestSeq) {
      todayLoading.value = false
    }
  }
}

/** 开课时间只展示时分 */
function clockOf(startTime) {
  return String(startTime || '').slice(11, 16)
}

/** 课程时长（分钟）：结束时间减开始时间 */
function durationOf(item) {
  const start = parseTime(item.startTime)
  const end = parseTime(item.endTime)
  if (start === null || end === null || end <= start) {
    return 0
  }
  return Math.round((end - start) / 60000)
}

/** 'yyyy-MM-dd HH:mm:ss' → 毫秒；小程序的 Date 不接受带空格的字符串，按分段构造 */
function parseTime(text) {
  const match = /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})$/.exec(String(text || ''))
  if (!match) {
    return null
  }
  return new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]),
    Number(match[4]), Number(match[5]), Number(match[6])).getTime()
}

function openBooking() {
  uni.switchTab({ url: '/pages/booking/index' })
}

function openSchedule(item) {
  if (!item || !item.id) {
    return
  }
  uni.navigateTo({ url: '/pages/detail/index?scheduleId=' + encodeURIComponent(item.id) })
}

/** 加载金牌教练：门店为空也照常请求，失败只影响本栏目 */
async function loadCoaches() {
  const seq = ++coachRequestSeq
  coachLoading.value = true
  coachError.value = false
  try {
    const list = await getFeaturedCoaches()
    if (seq !== coachRequestSeq) {
      return
    }
    coaches.value = list
  } catch (error) {
    if (seq !== coachRequestSeq) {
      return
    }
    coachError.value = true
    coaches.value = []
  } finally {
    if (seq === coachRequestSeq) {
      coachLoading.value = false
    }
  }
}

onShow(async () => {
  // 每次回到首页都校准一次：从「换店」或别处返回时保证门店卡与金牌列表是最新的
  loadCoaches()
  await loadCurrentStore()
  loadToday()
})

/* ---------------- 换店 ---------------- */

function openPicker() {
  pickerVisible.value = true
  keyword.value = ''
  activeRegion.value = ''
  pickerList.value = []
  pickerPage.value = 1
  pickerTotal.value = 0
  pickerFinished.value = false
  pickerEmpty.value = false
  loadRegions()
  loadPickerPage(1)
}

function closePicker() {
  pickerVisible.value = false
}

/**
 * 区域选项：从一次不带筛选的门店列表里归纳 regionCode + regionName
 * （用户端没有区域字典接口，区域就是「已存在门店的所在区」）。
 * 列表上限 pageSize=100，所以区域候选也是在前 100 家门店里归纳。
 */
async function loadRegions() {
  try {
    const all = await listStores({ pageNum: 1, pageSize: 100 })
    const seen = {}
    const out = []
    for (let i = 0; i < all.length; i++) {
      const code = all[i].regionCode
      if (code && !seen[code]) {
        seen[code] = true
        out.push({ code: code, name: all[i].regionName || code })
      }
    }
    regions.value = out
  } catch (error) {
    regions.value = []
  }
}

async function loadPickerPage(page) {
  if (pickerLoading.value) {
    return
  }
  pickerLoading.value = true
  try {
    const list = await listStores({
      regionCode: activeRegion.value,
      keyword: keyword.value.trim(),
      pageNum: page,
      pageSize: PICKER_PAGE_SIZE
    })
    pickerTotal.value = Number(list.total) || 0
    pickerPage.value = page
    pickerList.value = page === 1 ? list.slice() : pickerList.value.concat(list)
    pickerFinished.value = list.length < PICKER_PAGE_SIZE || pickerList.value.length >= pickerTotal.value
    pickerEmpty.value = pickerList.value.length === 0
  } catch (error) {
    if (page === 1) {
      pickerList.value = []
      pickerEmpty.value = true
    }
    pickerFinished.value = true
  } finally {
    pickerLoading.value = false
  }
}

function onKeywordConfirm() {
  loadPickerPage(1)
}

function onClearKeyword() {
  keyword.value = ''
  loadPickerPage(1)
}

function onRegionTap(code) {
  if (activeRegion.value === code) {
    return
  }
  activeRegion.value = code
  loadPickerPage(1)
}

function onPickerScrollLower() {
  if (pickerFinished.value || pickerLoading.value) {
    return
  }
  loadPickerPage(pickerPage.value + 1)
}

function isCurrent(item) {
  return String(item.id) === String(store.value.id || '')
}

/** 选中门店：写本地存储（id 为唯一事实来源）并即时刷新首页门店卡 */
function pickStore(item) {
  const saved = saveCurrentStore(item)
  store.value = saved || item
  loaded.value = true
  pickerVisible.value = false
  uni.showToast({ title: '已切换门店', icon: 'none' })
  loadToday()
}

/* ---------------- 金牌教练 ---------------- */

function openCoach(coach) {
  if (!coach || !coach.id) {
    return
  }
  uni.navigateTo({ url: '/pages/coaches/index?coachId=' + encodeURIComponent(coach.id) })
}

function onAvatarError(coachId) {
  avatarFailed.value[coachId] = true
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
  margin: 24rpx $ys-gap 0;
  border-radius: $ys-radius-lg;
  background: #fff;
  box-shadow: $ys-card-shadow;
  overflow: hidden;

  &__hero {
    display: block;
    width: 100%;
    height: 280rpx;
    background: $ys-line;
  }

  &__body {
    padding: 26rpx 25rpx 28rpx;
  }

  &__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__name {
    flex: 1;
    font-size: 33rpx;
    font-weight: 600;
    line-height: 1.2;
    color: $ys-title;
  }

  &__change {
    flex: none;
    margin-left: 15rpx;
    padding: 4rpx 15rpx;
    border-radius: 999px;
    background: $ys-brand-soft;
  }

  &__change-text {
    font-size: 23rpx;
    line-height: 1.4;
    color: $ys-brand-link;
  }

  &__meta {
    display: flex;
    align-items: center;
    margin-top: 18rpx;

    &--addr {
      margin-top: 12rpx;
    }
  }

  &__icon {
    flex: none;
    line-height: 1;
  }

  &__region {
    margin-left: 8rpx;
    font-size: 25rpx;
    line-height: 1.3;
    color: $ys-text-sub;
  }

  &__addr {
    margin-left: 8rpx;
    flex: 1;
    font-size: 25rpx;
    line-height: 1.3;
    color: $ys-ink-2;
  }
}

.home__empty {
  padding-top: 40rpx;
}

/* ---------------- 今日可约团课 ---------------- */
.home-today {
  margin-top: 42rpx;
  padding: 0 $ys-gap;

  &__head {
    padding: 0 3rpx 22rpx;
  }

  &__state {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 40rpx 0 10rpx;
  }

  &__empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 85rpx;
    border: 1px solid $ys-line;
    border-radius: $ys-radius-sm;
  }

  &__state-text {
    font-size: 25rpx;
    line-height: 1.4;
    color: $ys-text-sub;
  }

  &__retry {
    margin-top: 20rpx;
    padding: 10rpx 34rpx;
    border-radius: 999px;
    background: $ys-brand-pale;
  }

  &__retry-text {
    font-size: 25rpx;
    line-height: 1.4;
    color: $ys-brand;
  }

  &__list {
    display: flex;
    flex-direction: column;
    border-radius: $ys-radius-lg;
    background: #fff;
    box-shadow: $ys-card-shadow;
  }
}

.today-item {
  display: flex;
  align-items: center;
  padding: 28rpx 24rpx;

  & + & {
    border-top: 1px solid $ys-line;
  }

  &__time {
    flex: none;
    width: 130rpx;
    display: flex;
    flex-direction: column;
  }

  &__clock {
    font-size: 38rpx;
    font-weight: 600;
    line-height: 1.2;
    color: $ys-title;
  }

  &__label {
    margin-top: 6rpx;
    font-size: 23rpx;
    line-height: 1.3;
    color: $ys-text-sub;
  }

  &__main {
    flex: 1;
    min-width: 0;
    margin-left: 12rpx;
    display: flex;
    flex-direction: column;
  }

  &__name {
    overflow: hidden;
    font-size: 33rpx;
    font-weight: 600;
    line-height: 1.3;
    color: $ys-title;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__meta {
    display: flex;
    flex-wrap: wrap;
    margin-top: 10rpx;
  }

  &__cell {
    display: flex;
    align-items: center;
    width: 50%;
    min-width: 0;
    margin-top: 8rpx;
  }

  &__text {
    overflow: hidden;
    margin-left: 6rpx;
    font-size: 25rpx;
    line-height: 1.3;
    color: $ys-text-card;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__go {
    flex: none;
    margin-left: 12rpx;
    padding: 14rpx 28rpx;
    border-radius: 999px;
    background: $ys-brand;
  }

  &__go-text {
    font-size: 27rpx;
    line-height: 1.2;
    color: #fff;
  }
}

/* ---------------- 金牌教练 ---------------- */
.home-coaches {
  margin-top: 42rpx;
  padding: 0 $ys-gap;

  &__head {
    padding: 0 3rpx 22rpx;
  }

  &__state {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 40rpx 0 10rpx;
  }

  &__state-text {
    font-size: 25rpx;
    line-height: 1.4;
    color: $ys-ink-3;
  }

  &__retry {
    margin-top: 20rpx;
    padding: 10rpx 34rpx;
    border-radius: 999px;
    background: $ys-brand-pale;
  }

  &__retry-text {
    font-size: 25rpx;
    line-height: 1.4;
    color: $ys-brand;
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: 20rpx;
  }
}

.home-coach {
  display: flex;
  align-items: center;
  padding: 22rpx 24rpx;
  background: #fff;
  border-radius: $ys-radius-lg;
  box-shadow: $ys-card-shadow;

  &__avatar {
    flex: none;
    width: 112rpx;
    height: 112rpx;
    border-radius: $ys-radius-md;
    background: $ys-line;

    &--empty {
      display: flex;
      align-items: center;
      justify-content: center;
      background: #eef3f7;
    }
  }

  &__main {
    flex: 1;
    min-width: 0;
    margin-left: 24rpx;
    display: flex;
    flex-direction: column;
  }

  &__name {
    overflow: hidden;
    font-size: 31rpx;
    font-weight: 600;
    line-height: 1.3;
    color: $ys-title;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__intro {
    overflow: hidden;
    margin-top: 10rpx;
    font-size: 25rpx;
    line-height: 1.4;
    color: $ys-ink-3;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.home__bottom {
  height: 15rpx;
}

/* ---------------- 换店弹层 ---------------- */
.picker {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 100;

  &__mask {
    position: absolute;
    left: 0;
    right: 0;
    top: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.4);
  }

  &__panel {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    box-sizing: border-box;
    height: 78vh;
    padding: 0 25rpx;
    display: flex;
    flex-direction: column;
    background: #fff;
    border-top-left-radius: $ys-radius-lg;
    border-top-right-radius: $ys-radius-lg;
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 96rpx;
    flex: none;
  }

  &__title {
    font-size: 31rpx;
    font-weight: 600;
    line-height: 1;
    color: $ys-ink-1;
  }

  &__close {
    font-size: 27rpx;
    line-height: 1;
    color: $ys-text-sub;
  }

  &__search {
    flex: none;
    display: flex;
    align-items: center;
    height: 72rpx;
    padding: 0 23rpx;
    border-radius: 999px;
    background: $ys-page;
  }

  &__search-icon {
    flex: none;
  }

  &__input {
    flex: 1;
    margin-left: 12rpx;
    height: 72rpx;
    font-size: 27rpx;
    color: $ys-ink-1;
  }

  &__clear {
    flex: none;
    margin-left: 12rpx;
    font-size: 25rpx;
    color: $ys-brand-link;
  }

  &__regions {
    flex: none;
    margin-top: 20rpx;
    width: 100%;
    white-space: nowrap;
  }

  &__list {
    flex: 1;
    margin-top: 12rpx;
    width: 100%;
  }

  &__state {
    padding-top: 20rpx;
  }

  &__items {
    padding-bottom: 40rpx;
  }

  &__tip {
    padding: 25rpx 0 10rpx;
    text-align: center;
    font-size: 23rpx;
    color: $ys-ink-3;
  }
}

.region-strip {
  display: inline-flex;
  align-items: center;
  gap: 15rpx;
  padding: 4rpx 0 12rpx;
}

.region-chip {
  flex: none;
  padding: 10rpx 25rpx;
  border-radius: 999px;
  background: $ys-page;

  &--on {
    background: $ys-brand-pale;

    .region-chip__text {
      color: $ys-brand;
    }
  }

  &__text {
    font-size: 25rpx;
    line-height: 1.3;
    color: $ys-ink-2;
  }
}

.pitem {
  display: flex;
  align-items: center;
  padding: 25rpx 0;
  border-bottom: 1px solid $ys-line;

  &__main {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  &__name {
    font-size: 29rpx;
    font-weight: 500;
    line-height: 1.3;
    color: $ys-ink-1;
  }

  &__sub {
    margin-top: 8rpx;
    font-size: 23rpx;
    line-height: 1.3;
    color: $ys-brand;
  }

  &__addr {
    margin-top: 8rpx;
    font-size: 23rpx;
    line-height: 1.3;
    color: $ys-ink-3;
  }
}
</style>
