<template>
  <!--
    约教练页：单教练公开资料 + 当前门店未来 14 天排课。
    路由参数 coachId（教练 ID 字符串）；缺失或非法时只显示参数错误，不请求其他教练资料。
    状态栏 / 导航栏（标题「约教练」）由 pages.json 与系统绘制，本页只画内容区。
  -->
  <view class="coach-page">
    <!-- 终态：参数错误 / 不存在 / 加载失败 -->
    <view v-if="paramError" class="coach-state">
      <ys-empty title="参数错误" subtitle="请从首页的金牌教练进入" :padding-top="160" />
      <view class="coach-state__btn press" @tap="goHome">
        <text class="coach-state__btn-text">返回首页</text>
      </view>
    </view>

    <view v-else-if="notFound" class="coach-state">
      <ys-empty title="教练不存在或已被删除" subtitle="链接可能已失效，请返回首页重新选择" :padding-top="160" />
      <view class="coach-state__btn press" @tap="goHome">
        <text class="coach-state__btn-text">返回首页</text>
      </view>
    </view>

    <view v-else-if="detailError" class="coach-state">
      <ys-empty title="加载失败" subtitle="网络异常，请稍后重试" :padding-top="160" />
      <view class="coach-state__btn press" @tap="reloadDetail">
        <text class="coach-state__btn-text">重试</text>
      </view>
    </view>

    <view v-else-if="detailLoading && !coach" class="coach-state">
      <text class="coach-state__text">加载中…</text>
    </view>

    <template v-else-if="coach">
      <!-- 渐变背景区：资料面板 + 门店/窗口说明 + 课种 Tab -->
      <view class="hero">
        <view class="profile">
          <image
            v-if="coach.avatarUrl && !avatarFailed"
            class="profile__avatar"
            :src="coach.avatarUrl"
            mode="aspectFill"
            @error="onAvatarError"
          />
          <view v-else class="profile__avatar profile__avatar--empty">
            <uni-icons type="person-filled" size="36" color="#ADBAC4" />
          </view>

          <text class="profile__name">{{ coach.name }}</text>
          <text class="profile__intro">{{ coach.intro || '暂无教练简介' }}</text>

          <text class="profile__gallery-title">教练相册</text>
          <view v-if="gallery.length" class="gallery">
            <view
              v-for="(url, index) in gallery"
              :key="url + '-' + index"
              class="gallery__item press"
              @tap="previewGallery(index)"
            >
              <image
                v-if="!galleryFailed[index]"
                class="gallery__img"
                :src="url"
                mode="aspectFill"
                @error="onGalleryError(index)"
              />
              <view v-else class="gallery__img gallery__img--empty">
                <uni-icons type="image" size="20" color="#ADBAC4" />
              </view>
            </view>
          </view>
          <text v-else class="profile__gallery-empty">暂无教练相册</text>
        </view>

        <!-- 排课范围说明：门店来自首页「换店」选择的本地存储 -->
        <view v-if="storeError" class="window">
          <text class="window__text window__text--warn">门店信息加载失败</text>
          <view class="window__retry press" @tap="retryStore">
            <text class="window__retry-text">重试</text>
          </view>
        </view>
        <view v-else-if="storeId" class="window">
          <text class="window__text">当前门店 · 未来14天</text>
          <text class="window__store">{{ storeName }}</text>
        </view>
        <view v-else class="window">
          <text class="window__text window__text--warn">请先在首页选择门店</text>
        </view>

        <view class="tabs">
          <view
            v-for="tab in courseTabs"
            :key="tab.value"
            class="tabs__item press"
            @tap="onTypeChange(tab.value)"
          >
            <view class="tabs__hit">
              <text class="tabs__text" :class="{ 'tabs__text--on': tab.value === activeType }">
                {{ tab.label }}
              </text>
              <view v-if="tab.value === activeType" class="tabs__hi" />
            </view>
          </view>
        </view>
      </view>

      <!-- 排课列表：当前门店 + 所选教练 + 课种，按开始时间升序分页 -->
      <view v-if="storeId && !storeError" class="schedules">
        <view v-if="scheduleFatal" class="schedules__state">
          <text class="schedules__state-text">{{ scheduleFatalText }}</text>
          <view class="schedules__actions">
            <view class="schedules__action press" @tap="goBack">
              <text class="schedules__action-text">返回</text>
            </view>
            <view class="schedules__action schedules__action--primary press" @tap="goHome">
              <text class="schedules__action-text schedules__action-text--primary">去首页换店</text>
            </view>
          </view>
        </view>

        <view v-else-if="firstPageError" class="schedules__state">
          <text class="schedules__state-text">排课加载失败</text>
          <view class="schedules__retry press" @tap="reloadSchedules">
            <text class="schedules__retry-text">重试</text>
          </view>
        </view>


        <view v-else-if="loading && !schedules.length" class="schedules__state">
          <text class="schedules__state-text">加载中…</text>
        </view>

        <view v-else-if="loaded && !schedules.length" class="schedules__state">
          <text class="schedules__state-text">暂无排课</text>
        </view>

        <view v-else class="schedules__list">
          <view
            v-for="item in schedules"
            :key="item.id"
            class="sched-card"
            :class="{ 'sched-card--off': !item.bookable }"
          >
            <image
              v-if="item.courseCoverUrl && !coverFailed[item.id]"
              class="sched-card__cover"
              :src="item.courseCoverUrl"
              mode="aspectFill"
              @error="onCoverError(item.id)"
            />
            <view v-else class="sched-card__cover sched-card__cover--empty">
              <uni-icons type="image" size="24" color="#ADBAC4" />
            </view>

            <view class="sched-card__main">
              <view class="sched-card__head">
                <view class="sched-card__title-box">
                  <text class="sched-card__type">{{ item.courseTypeName }}</text>
                  <text class="sched-card__name">{{ item.courseName }}</text>
                </view>
                <text class="sched-card__status" :class="statusClass(item)">{{ item.statusText }}</text>
              </view>

              <text class="sched-card__line">时间 {{ item.scheduleDate }} {{ hm(item.startTime) }}-{{ hm(item.endTime) }}</text>
              <text class="sched-card__line">难度 {{ item.difficulty }} · {{ item.classroomName || '教室待定' }}</text>

              <view class="sched-card__foot">
                <text class="sched-card__remaining">剩余 {{ item.remainingPlaces }} 个名额</text>
                <text class="sched-card__arrow" decode>{{ '>' }}</text>
              </view>
            </view>
          </view>

          <view v-if="appendError" class="schedules__state schedules__state--append">
            <text class="schedules__state-text">加载更多失败</text>
            <view class="schedules__retry press" @tap="retryAppend">
              <text class="schedules__retry-text">重试</text>
            </view>
          </view>
          <view v-else-if="hasMore" class="schedules__more press" @tap="onLoadMore">
            <text class="schedules__more-text">{{ loading ? '加载中…' : '加载更多' }}</text>
          </view>
          <view v-else class="schedules__end">
            <text class="schedules__end-text">没有更多了</text>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onHide, onLoad, onReachBottom, onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { getCoachDetail, listCoachSchedules } from '@/api/coach'
import { getCurrentStore } from '@/api/store'

/** 约教练页（单教练资料 + 当前门店排课） */

const PAGE_SIZE = 10

/** 课种枚举与后端契约一致：1 团课、2 精品课、3 私教课、4 特色课 */
const courseTabs = ref([
  { value: 1, label: '团课' },
  { value: 2, label: '精品课' },
  { value: 3, label: '私教课' },
  { value: 4, label: '特色课' }
])

const coachId = ref('')
const coach = ref(null)
const detailLoading = ref(true)
const detailError = ref(false)
const notFound = ref(false)
const paramError = ref(false)
const avatarFailed = ref(false)
const galleryFailed = ref({})

const storeId = ref('')
const storeName = ref('')
const storeError = ref(false)
const activeType = ref(1)
const schedules = ref([])
const total = ref(0)
const loadedPage = ref(0)
const loading = ref(false)
const loaded = ref(false)
const firstPageError = ref(false)
const appendError = ref(false)
const scheduleFatal = ref(false)
const scheduleFatalText = ref('')
const coverFailed = ref({})

let detailSeq = 0
let scheduleSeq = 0

const gallery = computed(() => (coach.value ? coach.value.gallery : []))
const hasMore = computed(() => schedules.value.length < total.value)

/** Long 最大值：19 位数字里超过它的 ID 后端会按非法参数返回 400 */
const LONG_MAX = '9223372036854775807'

/** 教练 ID 是雪花 ID 字符串：只接受 Long 范围内的正整数，空值、超长和溢出值都不发请求 */
function isValidCoachId(value) {
  const text = String(value || '')
  if (!/^[1-9]\d{0,18}$/.test(text)) {
    return false
  }
  return text.length < 19 || text <= LONG_MAX
}

/* ---------------- 资料 ---------------- */

async function loadDetail() {
  if (!isValidCoachId(coachId.value)) {
    paramError.value = true
    detailLoading.value = false
    return
  }
  const seq = ++detailSeq
  detailLoading.value = true
  detailError.value = false
  notFound.value = false
  paramError.value = false
  // 重新加载资料时复位图片失败标记，避免旧图片的错误状态影响新数据
  avatarFailed.value = false
  galleryFailed.value = {}
  try {
    const data = await getCoachDetail(coachId.value)
    if (seq !== detailSeq) {
      return
    }
    if (!data) {
      coach.value = null
      notFound.value = true
      return
    }
    coach.value = data
  } catch (error) {
    if (seq !== detailSeq) {
      return
    }
    // 400 = 教练 ID 非法（如超出 Long 范围），404 = 教练不存在或已删除，其余按可重试的接口失败处理
    if (error === 400) {
      coach.value = null
      paramError.value = true
    } else if (error === 404) {
      coach.value = null
      notFound.value = true
    } else {
      detailError.value = true
    }
  } finally {
    if (seq === detailSeq) {
      detailLoading.value = false
    }
  }
}

function reloadDetail() {
  loadDetail()
}

function onAvatarError() {
  avatarFailed.value = true
}

function onGalleryError(index) {
  galleryFailed.value[index] = true
}

function previewGallery(index) {
  const urls = gallery.value.slice()
  if (!urls.length) {
    return
  }
  uni.previewImage({ current: urls[index], urls: urls })
}

/* ---------------- 当前门店 ---------------- */

/** 返回门店是否发生变化；门店接口失败时保留原门店并标记 storeError，排课区暂不请求 */
async function calibrateStore() {
  try {
    const store = await getCurrentStore()
    storeError.value = false
    const nextId = store ? String(store.id) : ''
    const nextName = store ? store.name || '' : ''
    if (nextId === storeId.value && nextName === storeName.value) {
      return false
    }
    storeId.value = nextId
    storeName.value = nextName
    return true
  } catch (error) {
    storeError.value = true
    return false
  }
}

/* ---------------- 排课 ---------------- */

/** 清空当前课种的列表与分页状态；下一帧由 fetchPage 进入加载态，避免旧卡片残留或闪空态 */
function resetScheduleState() {
  scheduleSeq++
  schedules.value = []
  total.value = 0
  loadedPage.value = 0
  loading.value = true
  loaded.value = false
  firstPageError.value = false
  appendError.value = false
  scheduleFatal.value = false
  scheduleFatalText.value = ''
  coverFailed.value = {}
}

/**
 * 拉取排课页。
 *
 * 每次请求递增序号，响应回来时序号已过期就丢弃：快速切课种或门店变化时，
 * 晚到的旧课种响应不能覆盖当前列表。append 只追加，不改变已加载页号。
 */
async function fetchPage(pageNum, append = false) {
  if (!isValidCoachId(coachId.value) || !storeId.value) {
    return
  }
  const seq = ++scheduleSeq
  loading.value = true
  if (!append) {
    firstPageError.value = false
  }
  appendError.value = false
  try {
    const res = await listCoachSchedules({
      coachId: coachId.value,
      storeId: storeId.value,
      courseType: activeType.value,
      pageNum: pageNum,
      pageSize: PAGE_SIZE
    })
    if (seq !== scheduleSeq) {
      return
    }
    total.value = res.total
    schedules.value = append ? schedules.value.concat(res.list) : res.list
    loadedPage.value = pageNum
    loaded.value = true
  } catch (error) {
    if (seq !== scheduleSeq) {
      return
    }
    // 400/404 是参数或数据不存在，重试不会变好；只对网络/未知失败提供重试
    if (error === 400 || error === 404) {
      scheduleFatal.value = true
      scheduleFatalText.value = error === 404 ? '门店或教练已不存在' : '排课查询参数有误'
      schedules.value = []
      total.value = 0
      loadedPage.value = 0
      loaded.value = true
    } else if (append) {
      appendError.value = true
    } else {
      schedules.value = []
      total.value = 0
      loadedPage.value = 0
      firstPageError.value = true
      loaded.value = true
    }
  } finally {
    if (seq === scheduleSeq) {
      loading.value = false
    }
  }
}

function reloadSchedules() {
  fetchPage(1)
}

function onLoadMore() {
  if (loading.value || !hasMore.value) {
    return
  }
  fetchPage(loadedPage.value + 1, true)
}

function retryAppend() {
  if (loading.value) {
    return
  }
  fetchPage(loadedPage.value + 1, true)
}

function onTypeChange(value) {
  if (value === activeType.value) {
    return
  }
  activeType.value = value
  // 先清空旧课种分页与卡片，新响应到达前旧卡片不得继续渲染或可点
  resetScheduleState()
  fetchPage(1)
}

/* ---------------- 展示 ---------------- */

function hm(value) {
  const text = String(value || '')
  return text.length >= 16 ? text.slice(11, 16) : text
}

/** 状态徽标配色由后端文案决定（可约 = 蓝，已取消 = 灰红，其余 = 灰） */
function statusClass(item) {
  if (item.bookable) {
    return 'sched-card__status--on'
  }
  return item.statusText === '已取消' ? 'sched-card__status--cancel' : 'sched-card__status--off'
}

function onCoverError(scheduleId) {
  coverFailed.value[scheduleId] = true
}

/* ---------------- 交互 ---------------- */

function goHome() {
  uni.switchTab({ url: '/pages/home/index' })
}

function goBack() {
  const pages = getCurrentPages()
  if (pages && pages.length > 1) {
    uni.navigateBack()
    return
  }
  goHome()
}

/** 门店信息失败后重试：成功后重新拉第一页排课 */
async function retryStore() {
  const changed = await calibrateStore()
  if (storeError.value || !storeId.value) {
    return
  }
  if (changed || !schedules.value.length) {
    resetScheduleState()
  }
  fetchPage(1)
}

/* ---------------- 生命周期 ---------------- */

/** 是否已经完成首次展示：区分首次进入与从其他页面返回，避免首屏重复请求 */
let hasShown = false
/** 上一次显示后是否离开过页面（进入课程详情等），返回时据此刷新排课 */
let hiddenSinceShow = false

onLoad((query) => {
  const raw = query && query.coachId !== undefined && query.coachId !== null ? String(query.coachId) : ''
  coachId.value = raw
  if (!isValidCoachId(raw)) {
    paramError.value = true
    loaded.value = true
    return
  }
  loadDetail()
})

onHide(() => {
  hiddenSinceShow = true
})

onShow(async () => {
  if (paramError.value || !isValidCoachId(coachId.value)) {
    return
  }
  const wasStoreError = storeError.value
  const isFirstShow = !hasShown
  const isReturn = hasShown && hiddenSinceShow
  hasShown = true
  hiddenSinceShow = false
  const changed = await calibrateStore()
  if (storeError.value) {
    return
  }
  if (!storeId.value) {
    resetScheduleState()
    loading.value = false
    loaded.value = true
    return
  }
  // 首次展示、门店变化/恢复、从课程详情等其他页面返回都要重新拉当前课种第一页，
  // activeType 保持不变；resetScheduleState 保持加载态，避免先闪“暂无排课”。
  if (isFirstShow || changed || wasStoreError || isReturn) {
    resetScheduleState()
    fetchPage(1)
  }
})

onReachBottom(() => {
  // 小程序触底复用与「加载更多」按钮相同的分页方法
  onLoadMore()
})

onPullDownRefresh(async () => {
  if (!paramError.value && isValidCoachId(coachId.value)) {
    const wasStoreError = storeError.value
    const changed = await calibrateStore()
    const tasks = [loadDetail()]
    if (!storeError.value && storeId.value) {
      if (changed || wasStoreError) {
        resetScheduleState()
      }
      tasks.push(fetchPage(1))
    }
    await Promise.all(tasks)
  }
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.coach-page {
  min-height: 100vh;
  background: #fff;
}

/* ---------------- 终态 ---------------- */
.coach-state {
  padding-top: 40rpx;

  &__text {
    display: block;
    padding-top: 200rpx;
    text-align: center;
    font-size: 27rpx;
    color: $ys-ink-3;
  }

  &__btn {
    margin: 56rpx auto 0;
    width: 300rpx;
    height: 80rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    background: $ys-brand;
    border-radius: 56rpx;
  }

  &__btn-text {
    font-size: 29rpx;
    line-height: 1;
    color: #fff;
  }
}

/* ---------------- 渐变背景区 ---------------- */
.hero {
  padding: 150rpx 0 34rpx;
  background: linear-gradient(180deg, #93cde9 0rpx, #b9e0f2 220rpx, #dff1fa 560rpx, #ffffff 820rpx);
}

/* 资料面板：白色圆角，头像悬浮在面板上沿 */
.profile {
  position: relative;
  margin: 0 40rpx;
  padding: 104rpx 40rpx 36rpx;
  background: #fff;
  border-radius: 36rpx;

  &__avatar {
    position: absolute;
    left: 50%;
    top: -78rpx;
    width: 150rpx;
    height: 150rpx;
    margin-left: -75rpx;
    border: 6rpx solid #fff;
    border-radius: 50%;
    background: $ys-line;

    &--empty {
      display: flex;
      align-items: center;
      justify-content: center;
      background: #eef3f7;
    }
  }

  &__name {
    display: block;
    text-align: center;
    font-size: 36rpx;
    font-weight: 600;
    line-height: 1.3;
    color: #2f3a42;
  }

  &__intro {
    display: block;
    margin-top: 36rpx;
    font-size: 27rpx;
    line-height: 1.6;
    color: #3d4750;
  }

  &__gallery-title {
    display: block;
    margin-top: 30rpx;
    font-size: 31rpx;
    font-weight: 600;
    line-height: 1.3;
    color: #2f3a42;
  }

  &__gallery-empty {
    display: block;
    margin-top: 16rpx;
    font-size: 27rpx;
    line-height: 1.4;
    color: $ys-ink-3;
  }
}

.gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 20rpx;

  &__item {
    flex: none;
  }

  &__img {
    display: block;
    width: 140rpx;
    height: 140rpx;
    border-radius: 16rpx;
    background: $ys-line;

    &--empty {
      display: flex;
      align-items: center;
      justify-content: center;
      background: #eef3f7;
    }
  }
}

/* 当前门店 · 未来14天 */
.window {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: 40rpx;

  &__text {
    font-size: 25rpx;
    line-height: 1.4;
    color: #47707f;

    &--warn {
      color: #b25a5a;
    }
  }

  &__store {
    margin-top: 8rpx;
    font-size: 23rpx;
    line-height: 1.3;
    color: #6a8b98;
  }

  &__retry {
    margin-top: 12rpx;
    padding: 8rpx 30rpx;
    border-radius: 999px;
    background: $ys-brand-pale;
  }

  &__retry-text {
    font-size: 24rpx;
    line-height: 1.4;
    color: $ys-brand;
  }
}

/* ---------------- 课种 Tab ---------------- */
.tabs {
  display: flex;
  align-items: center;
  height: 96rpx;
  margin-top: 16rpx;
  padding: 0 $ys-gap;

  &__item {
    flex: 1;
    display: flex;
    justify-content: center;
  }

  &__hit {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    height: 44rpx;
  }

  &__text {
    font-size: 33rpx;
    line-height: 1;
    color: #4c555c;

    &--on {
      position: relative;
      z-index: 10;
      font-weight: 600;
      color: $ys-brand-tab;
    }
  }

  &__hi {
    position: absolute;
    left: 50%;
    bottom: -2rpx;
    width: 96rpx;
    height: 8rpx;
    margin-left: -48rpx;
    border-radius: 999px;
    background: $ys-brand-tab;
  }
}

/* ---------------- 排课区 ---------------- */
.schedules {
  padding: 34rpx $ys-gap 60rpx;
  background: #fff;

  &__list {
    display: flex;
    flex-direction: column;
    gap: 24rpx;
  }

  &__state {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 90rpx 0 20rpx;

    &--append {
      padding: 26rpx 0 0;
    }
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

  &__actions {
    display: flex;
    align-items: center;
    gap: 20rpx;
    margin-top: 24rpx;
  }

  &__action {
    padding: 12rpx 34rpx;
    border: 1px solid $ys-line;
    border-radius: 999px;

    &--primary {
      border-color: $ys-brand;
      background: $ys-brand;
    }
  }

  &__action-text {
    font-size: 25rpx;
    line-height: 1.4;
    color: $ys-ink-2;

    &--primary {
      color: #fff;
    }
  }

  &__more {
    margin-top: 28rpx;
    height: 76rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    border: 1px solid $ys-brand-pale;
    border-radius: 999px;
  }

  &__more-text {
    font-size: 27rpx;
    line-height: 1;
    color: $ys-brand;
  }

  &__end {
    display: flex;
    justify-content: center;
    padding: 30rpx 0 4rpx;
  }

  &__end-text {
    font-size: 23rpx;
    line-height: 1.3;
    color: $ys-ink-3;
  }
}

/* ---------------- 排课卡 ---------------- */
.sched-card {
  display: flex;
  padding: 22rpx;
  background: #e6f2fb;
  border-radius: 24rpx;

  &--off {
    background: #eef3f7;

    .sched-card__cover {
      opacity: 0.6;
    }
  }

  &__cover {
    flex: none;
    width: 160rpx;
    height: 160rpx;
    border-radius: 16rpx;
    background: #d5e6f2;

    &--empty {
      display: flex;
      align-items: center;
      justify-content: center;
      background: #d9e7f1;
    }
  }

  &__main {
    flex: 1;
    min-width: 0;
    margin-left: 22rpx;
    display: flex;
    flex-direction: column;
  }

  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
  }

  &__title-box {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
  }

  &__type {
    flex: none;
    margin-right: 12rpx;
    padding: 2rpx 12rpx;
    font-size: 21rpx;
    line-height: 1.5;
    color: #5b7c8d;
    background: #c9d8e6;
    border-radius: 8rpx;
  }

  &__name {
    flex: 1;
    overflow: hidden;
    font-size: 30rpx;
    font-weight: 600;
    line-height: 1.4;
    color: #2f3a42;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__status {
    flex: none;
    margin-left: 12rpx;
    padding: 4rpx 12rpx;
    font-size: 21rpx;
    line-height: 1.4;
    border-radius: 8rpx;

    &--on {
      color: $ys-brand;
      background: rgba(255, 255, 255, 0.75);
    }

    &--off {
      color: $ys-ink-2;
      background: rgba(255, 255, 255, 0.7);
    }

    &--cancel {
      color: #b25a5a;
      background: rgba(255, 255, 255, 0.7);
    }
  }

  &__line {
    display: block;
    margin-top: 10rpx;
    font-size: 25rpx;
    line-height: 1.4;
    color: #52616c;
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 10rpx;
  }

  &__remaining {
    flex: 1;
    font-size: 25rpx;
    line-height: 1.4;
    color: #52616c;
  }

  &__arrow {
    flex: none;
    font-size: 26rpx;
    line-height: 1.2;
    color: #7d95a4;
  }
}
</style>
