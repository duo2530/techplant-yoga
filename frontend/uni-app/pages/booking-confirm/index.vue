<template>
  <!--
    立即预约页
    路由参数：scheduleId（字符串形式的雪花 ID）
    数据：GET /api/schedules/{scheduleId} 与 GET /api/member-cards

    会员卡是否可用以前端规则做 best-effort 预判（后端提交时仍会再校验一次）；
    排课不存在 / 已删除 / 待上架时后端统一返回 404 → 友好提示并返回上一页。
  -->
  <view class="confirm">
    <view v-if="detail" class="confirm__body">
      <!-- 顶部课程卡 -->
      <view class="ccard">
        <image class="ccard__cover" :src="coverOf" mode="aspectFill" />
        <view class="ccard__main">
          <text class="ccard__name">{{ detail.courseName }}</text>
          <text class="ccard__intro">{{ detail.courseIntro || '暂无简介' }}</text>
        </view>
      </view>

      <!-- 门店卡 -->
      <view class="store">
        <view class="store__icon">
          <uni-icons type="shop" size="17" color="#609CE9" />
        </view>
        <view class="store__main">
          <text class="store__name">{{ detail.storeName || '门店待定' }}</text>
          <view class="store__addr">
            <uni-icons class="store__pin" type="location" size="13" color="#999999" />
            <text class="store__addr-text">{{ detail.storeAddress || '门店地址待定' }}</text>
          </view>
        </view>
      </view>

      <!-- 排课信息 -->
      <view class="info">
        <view class="row">
          <text class="row__label">难度</text>
          <view class="stars">
            <uni-icons
              v-for="n in STAR_TOTAL"
              :key="n"
              class="stars__item"
              :type="n <= difficulty ? 'star-filled' : 'star'"
              size="17"
              :color="n <= difficulty ? '#FBC02D' : '#D8DEE6'"
            />
          </view>
        </view>

        <view class="row">
          <text class="row__label">上课时间</text>
          <text class="row__value">{{ timeText }}</text>
        </view>

        <view class="row">
          <text class="row__label">人数</text>
          <view class="stepper">
            <view
              class="stepper__btn press"
              :class="{ 'stepper__btn--off': !canStep }"
              @tap="stepPerson(-1)"
            >
              <uni-icons type="minus" size="15" :color="canStep ? '#666666' : '#C6CCD4'" />
            </view>
            <view class="stepper__box">
              <text class="stepper__num">{{ persons }}</text>
            </view>
            <view
              class="stepper__btn press"
              :class="{ 'stepper__btn--off': !canIncrease }"
              @tap="stepPerson(1)"
            >
              <uni-icons type="plus" size="15" :color="canIncrease ? '#666666' : '#C6CCD4'" />
            </view>
          </view>
        </view>

        <view class="row row--last">
          <text class="row__label">时长</text>
          <text class="row__value">{{ durationMinutes }}分钟</text>
        </view>
      </view>

      <!-- 会员卡支付 -->
      <view class="pay">
        <text class="pay__title">会员卡支付</text>

        <view v-if="cards.length" class="pay__list">
          <view
            v-for="card in cards"
            :key="card.id"
            class="mcard press"
            :class="{
              'mcard--on': card.id === selectedCardId,
              'mcard--off': !card.usable
            }"
            @tap="onCardTap(card)"
          >
            <view class="mcard__head">
              <view class="mcard__badge" :class="{ 'mcard__badge--off': !card.usable }">
                <text class="mcard__badge-text">{{ card.cardTypeText || '会员卡' }}</text>
              </view>
              <text class="mcard__name">{{ card.cardName }}</text>
              <view v-if="card.id === selectedCardId" class="mcard__check">
                <uni-icons type="checkmarkempty" size="14" color="#FFFFFF" />
              </view>
            </view>

            <text class="mcard__meta">{{ cardMeta(card) }}</text>
            <text v-if="!card.usable" class="mcard__reason">{{ card.reason }}</text>
          </view>
        </view>

        <view v-if="cards.length && !hasUsableCard" class="pay__tip">
          <text class="pay__tip-text">暂无可用会员卡，请先开通会员卡</text>
        </view>

        <view v-else-if="!cards.length" class="pay__none">
          <text class="pay__none-text">暂无可用会员卡，请先开通会员卡</text>
        </view>
      </view>
    </view>

    <!-- 排课不存在 / 已删除 / 待上架（后端统一 404）或未登录 → 提示 + 返回上一页 -->
    <view v-else-if="blocked" class="nofound">
      <ys-empty
        image="/static/images/empty/booking.png"
        image-width="265rpx"
        image-height="108rpx"
        :title="blockedTitle"
        :subtitle="blockedSubtitle"
        :padding-top="150"
      />
      <view class="nofound__btn press" @tap="goBack">
        <text>返回</text>
      </view>
    </view>

    <!-- 底部固定操作条 -->
    <view v-if="detail" class="bar">
      <text v-if="!bookable" class="bar__hint">{{ hintText }}</text>
      <view class="bar__btn" :class="{ 'bar__btn--off': !canSubmit }" @tap="onSubmit">
        <text>{{ submitText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { getScheduleDetail } from '@/api/schedule'
import { listMemberCards } from '@/api/member'
import { createReservation, generateRequestId } from '@/api/booking'
import { isSessionValid, LOGIN_PAGE } from '@/utils/session'

/** 立即预约页 */

/** 难度星级上限 */
const STAR_TOTAL = 5

/** 会员卡状态：仅「正常」可用来支付 */
const CARD_STATUS_NORMAL = 2

/** 卡种范围：1 团课类、2 私教课 */
const SCOPE_GROUP = 1
const SCOPE_PRIVATE = 2

/** 团课类（1/2/4）与私教课（3）分别适用的排课课种 */
const GROUP_COURSE_TYPES = [1, 2, 4]

/** 卡类型：1 次数卡、2 年卡 */
const CARD_TYPE_TIMES = 1
const CARD_TYPE_ANNUAL = 2

const scheduleId = ref('')
const detail = ref(null)
const cards = ref([])
const selectedCardId = ref('')
const persons = ref(1)
const submitting = ref(false)

/** 排课不存在或未登录：两种都不是「可以预约」的状态 */
const blocked = ref(false)
const blockedTitle = ref('课程不存在或已下架')
const blockedSubtitle = ref('即将返回上一页')

/** 本次预约的幂等请求号：提交失败重试复用同一个，成功后才换新的 */
const requestId = ref('')

let backTimer = null
let loginRedirected = false

const coverOf = computed(() => {
  return (detail.value && detail.value.courseCoverUrl) || '/static/images/booking/cover-1.jpg'
})

const difficulty = computed(() => {
  const value = Number(detail.value ? detail.value.difficulty : 0)
  return isNaN(value) ? 0 : value
})

const timeText = computed(() => {
  if (!detail.value) {
    return ''
  }
  const date = detail.value.scheduleDate || ''
  const start = hm(detail.value.startTime)
  const end = hm(detail.value.endTime)
  const range = start && end ? start + ' - ' + end : start || end || '时间待定'
  return date ? date + ' ' + range : range
})

/** 时长：起止时间都拿到才算得出，否则不显示分钟数 */
const durationMinutes = computed(() => {
  if (!detail.value) {
    return 0
  }
  const start = parseTime(detail.value.startTime)
  const end = parseTime(detail.value.endTime)
  if (start === null || end === null || end <= start) {
    return 0
  }
  return Math.round((end - start) / 60000)
})

const bookable = computed(() => !!(detail.value && detail.value.bookable))

const selectedCard = computed(() => {
  return cards.value.find((card) => card.id === selectedCardId.value) || null
})

/** 年卡一次只供一人，人数上限固定为 1 */
const maxPersons = computed(() => {
  const card = selectedCard.value
  if (!card || !card.usable) {
    return 1
  }
  if (card.cardType === CARD_TYPE_ANNUAL) {
    return 1
  }
  const places = Number(detail.value ? detail.value.remainingPlaces : 0) || 0
  const times = card.remainingTimes === null ? null : Number(card.remainingTimes)
  if (times === null) {
    return Math.max(1, places)
  }
  return Math.max(1, Math.min(places, times))
})

const canStep = computed(() => maxPersons.value > 1)

/** 未到上限才能继续加人 */
const canIncrease = computed(() => persons.value < maxPersons.value)

/** 至少有一张可用会员卡才允许提交 */
const hasUsableCard = computed(() => cards.value.some((card) => card.usable))

/** 排课不可约时的状态提示，用后端 statusText */
const hintText = computed(() => {
  const text = detail.value ? detail.value.statusText : ''
  return text || '当前排课不可预约'
})

const submitText = computed(() => {
  if (submitting.value) {
    return '提交中...'
  }
  return bookable.value ? '立即预约' : '不可预约'
})

const canSubmit = computed(() => bookable.value && !!selectedCard.value && !submitting.value)

function hm(value) {
  const text = String(value || '')
  return text.length >= 16 ? text.slice(11, 16) : text
}

/** 把 yyyy-MM-dd HH:mm:ss 解析成毫秒时间戳；解析不出来返回 null */
function parseTime(value) {
  const text = String(value || '').replace(/-/g, '/')
  const stamp = new Date(text).getTime()
  return isNaN(stamp) ? null : stamp
}

/** 单张卡是否可用来支付本次排课 */
function unusableReason(card) {
  if (Number(card.status) !== CARD_STATUS_NORMAL) {
    return card.statusText || '会员卡不可用'
  }
  const courseType = Number(detail.value ? detail.value.courseType : 0)
  if (card.courseScope === SCOPE_GROUP && GROUP_COURSE_TYPES.indexOf(courseType) < 0) {
    return '不适用本课种'
  }
  if (card.courseScope === SCOPE_PRIVATE && courseType !== 3) {
    return '仅限私教课'
  }
  if (card.cardType === CARD_TYPE_TIMES) {
    const store = detail.value ? detail.value.storeName : ''
    if (card.storeName && store && card.storeName !== store) {
      return '本卡仅限' + card.storeName + '使用'
    }
    if (card.remainingTimes !== null && Number(card.remainingTimes) <= 0) {
      return '剩余次数不足'
    }
  }
  return ''
}

/** 卡片副标题：次数卡看剩余次数，年卡看剩余天数，字段为空就不显示 */
function cardMeta(card) {
  const parts = []
  if (card.cardType === CARD_TYPE_TIMES) {
    if (card.remainingTimes !== null) {
      parts.push('剩余 ' + card.remainingTimes + ' 次')
    }
  } else if (card.cardType === CARD_TYPE_ANNUAL) {
    if (card.remainingDays !== null) {
      parts.push('剩余 ' + card.remainingDays + ' 天')
    }
  }
  if (card.storeName) {
    parts.push(card.storeName)
  }
  return parts.join(' · ')
}

/** 给每张卡补上可用性与不可用原因 */
function decorate(rows) {
  return rows.map((card) => {
    const reason = unusableReason(card)
    return Object.assign({}, card, { usable: !reason, reason: reason })
  })
}

/** 自动选中第一张可用卡；用户手动选过就不再改选 */
function pickCard() {
  const current = cards.value.find((card) => card.id === selectedCardId.value)
  if (current && current.usable) {
    return
  }
  const first = cards.value.find((card) => card.usable)
  selectedCardId.value = first ? first.id : ''
}

/** 切换会员卡后重新夹紧人数 */
function clampPersons() {
  const max = maxPersons.value
  if (persons.value > max) {
    persons.value = max
  }
  if (persons.value < 1) {
    persons.value = 1
  }
}

function stepPerson(delta) {
  if (!canStep.value) {
    return
  }
  const next = persons.value + delta
  if (next < 1 || next > maxPersons.value) {
    return
  }
  persons.value = next
}

function onCardTap(card) {
  if (!card.usable) {
    return
  }
  selectedCardId.value = card.id
  clampPersons()
}

onLoad((query) => {
  scheduleId.value = query && query.scheduleId ? String(query.scheduleId) : ''
  requestId.value = generateRequestId()

  if (!isSessionValid()) {
    if (!loginRedirected) {
      loginRedirected = true
      uni.navigateTo({ url: LOGIN_PAGE })
    }
    blockedTitle.value = '登录后即可预约'
    blockedSubtitle.value = '请在登录页完成登录'
    blocked.value = true
    return
  }

  load()
})

onUnload(() => {
  if (backTimer) {
    clearTimeout(backTimer)
    backTimer = null
  }
})

async function load() {
  if (!scheduleId.value) {
    onNotFound()
    return
  }
  try {
    const [schedule, memberCards] = await Promise.all([
      getScheduleDetail(scheduleId.value),
      listMemberCards({ pageNum: 1, pageSize: 100 })
    ])
    if (!schedule) {
      onNotFound()
      return
    }
    detail.value = schedule
    cards.value = decorate(Array.isArray(memberCards) ? memberCards : [])
    pickCard()
    clampPersons()
  } catch (error) {
    // 404（不存在 / 已删除 / 待上架）与 500（参数非法）都由 utils/request 统一 toast 服务端 msg
    onNotFound()
  }
}

/** 友好提示 + 自动返回上一页（没有上一页时回约课 Tab） */
function onNotFound() {
  blocked.value = true
  blockedTitle.value = '课程不存在或已下架'
  blockedSubtitle.value = '即将返回上一页'
  if (backTimer) {
    clearTimeout(backTimer)
  }
  backTimer = setTimeout(() => {
    backTimer = null
    goBack()
  }, 1500)
}

function goBack() {
  const pages = getCurrentPages()
  if (pages && pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.switchTab({ url: '/pages/booking/index' })
}

async function onSubmit() {
  if (!bookable.value) {
    uni.showToast({ title: hintText.value, icon: 'none' })
    return
  }
  if (!selectedCard.value) {
    uni.showToast({ title: '暂无可用会员卡，请先开通会员卡', icon: 'none' })
    return
  }
  if (submitting.value) {
    return
  }
  submitting.value = true
  try {
    await createReservation({
      requestId: requestId.value,
      scheduleId: scheduleId.value,
      memberCardId: selectedCardId.value,
      persons: persons.value
    })
    requestId.value = generateRequestId()
    const current = detail.value || {}
    const query = [
      'courseName=' + encodeURIComponent(current.courseName || ''),
      'scheduleDate=' + encodeURIComponent(current.scheduleDate || ''),
      'startTime=' + encodeURIComponent(hm(current.startTime))
    ].join('&')
    uni.redirectTo({ url: '/pages/booking-success/index?' + query })
  } catch (error) {
    // 失败原因已由 utils/request 统一 toast（409 的不可预约 / 人数超限等都在 msg 里）
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.confirm {
  min-height: 100vh;
  background: $ys-page;

  &__body {
    padding-bottom: 180rpx;
  }
}

/* ---------------- 顶部课程卡 ---------------- */
.ccard {
  display: flex;
  margin: 24rpx $ys-gap 0;
  padding: 25rpx;
  background: #fff;
  border-radius: $ys-radius-lg;

  &__cover {
    flex: none;
    width: 173rpx;
    height: 173rpx;
    border-radius: $ys-radius-md;
    background: $ys-line;
  }

  &__main {
    flex: 1;
    margin-left: 23rpx;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  &__name {
    font-size: 33rpx;
    font-weight: 600;
    line-height: 1.3;
    color: #303339;
  }

  &__intro {
    margin-top: 14rpx;
    font-size: 25rpx;
    line-height: 1.5;
    color: $ys-ink-3;
  }
}

/* ---------------- 门店卡 ---------------- */
.store {
  display: flex;
  align-items: center;
  margin: 20rpx $ys-gap 0;
  padding: 25rpx;
  background: #fff;
  border: 1px solid $ys-line;
  border-radius: $ys-radius-md;

  &__icon {
    flex: none;
    width: 58rpx;
    height: 58rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    background: $ys-brand-pale;
    border-radius: $ys-radius-sm;
  }

  &__main {
    flex: 1;
    margin-left: 18rpx;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  &__name {
    font-size: 29rpx;
    font-weight: 600;
    line-height: 1.3;
    color: $ys-ink-1;
  }

  &__addr {
    display: flex;
    align-items: center;
    margin-top: 8rpx;
  }

  &__pin {
    flex: none;
  }

  &__addr-text {
    flex: 1;
    margin-left: 6rpx;
    font-size: 23rpx;
    line-height: 1.4;
    color: $ys-ink-3;
  }
}

/* ---------------- 排课信息 ---------------- */
.info {
  margin-top: 20rpx;
  padding: 0 25rpx 12rpx;
  background: #fff;
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 108rpx;

  &__label {
    flex: none;
    font-size: 29rpx;
    line-height: 1.3;
    color: #303339;
  }

  &__value {
    flex: 1;
    text-align: right;
    font-size: 29rpx;
    line-height: 1.3;
    color: #303339;
  }
}

.stars {
  display: flex;
  align-items: center;

  &__item {
    margin-left: 10rpx;
  }
}

.stepper {
  display: flex;
  align-items: center;

  &__btn {
    width: 62rpx;
    height: 58rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #f1f3f5;

    /* 到达上下限：不可再点 */
    &--off {
      background: #f7f8fa;
    }
  }

  &__box {
    width: 84rpx;
    height: 58rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #fff;
    border-top: 1px solid #e6e8eb;
    border-bottom: 1px solid #e6e8eb;
  }

  &__num {
    font-size: 28rpx;
    line-height: 1;
    color: #303339;
  }
}

/* ---------------- 会员卡支付 ---------------- */
.pay {
  margin-top: 20rpx;
  padding: 28rpx $ys-gap 34rpx;
  background: #fff;

  &__title {
    display: block;
    font-size: 31rpx;
    font-weight: 600;
    line-height: 1.3;
    color: #303339;
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: 18rpx;
    margin-top: 24rpx;
  }

  &__none {
    margin-top: 24rpx;
    padding: 60rpx 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: $ys-page;
    border-radius: $ys-radius-sm;
  }

  &__none-text {
    font-size: 25rpx;
    line-height: 1.4;
    color: $ys-ink-3;
  }

  /* 卡都在，但一张都不可用 */
  &__tip {
    margin-top: 20rpx;
    padding: 22rpx 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: $ys-page;
    border-radius: $ys-radius-sm;
  }

  &__tip-text {
    font-size: 25rpx;
    line-height: 1.4;
    color: $ys-ink-3;
  }
}

.mcard {
  padding: 23rpx 25rpx;
  border: 2rpx solid $ys-line;
  border-radius: $ys-radius-md;
  background: #fff;

  /* 选中：主色描边 + 浅蓝底 */
  &--on {
    border-color: $ys-brand;
    background: $ys-brand-pale;
  }

  /* 不可用：置灰，仍展示不可用原因 */
  &--off {
    background: #f5f6f8;

    .mcard__name,
    .mcard__meta {
      color: $ys-ink-3;
    }
  }

  &__head {
    display: flex;
    align-items: center;
  }

  &__badge {
    flex: none;
    padding: 3rpx 12rpx;
    background: $ys-brand;
    border-radius: 8rpx;

    &--off {
      background: #c6ccd4;
    }
  }

  &__badge-text {
    font-size: 21rpx;
    line-height: 1.4;
    color: #fff;
  }

  &__name {
    flex: 1;
    margin-left: 14rpx;
    font-size: 29rpx;
    font-weight: 600;
    line-height: 1.3;
    color: $ys-ink-1;
  }

  &__check {
    flex: none;
    width: 34rpx;
    height: 34rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    background: $ys-brand;
    border-radius: 50%;
  }

  &__meta {
    display: block;
    margin-top: 12rpx;
    font-size: 25rpx;
    line-height: 1.4;
    color: $ys-ink-2;
  }

  &__reason {
    display: block;
    margin-top: 8rpx;
    font-size: 23rpx;
    line-height: 1.4;
    color: #b25a5a;
  }
}

/* ---------------- 找不到排课 / 未登录 ---------------- */
.nofound {
  padding-top: 60rpx;

  &__btn {
    margin: 60rpx auto 0;
    width: 300rpx;
    height: 80rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 29rpx;
    color: #fff;
    background: $ys-brand;
    border-radius: 56rpx;
  }
}

/* ---------------- 底部操作条 ---------------- */
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 16rpx 25rpx 30rpx;
  background: #fff;
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.05);

  &__hint {
    display: block;
    margin-bottom: 12rpx;
    text-align: center;
    font-size: 23rpx;
    line-height: 1.3;
    color: #b25a5a;
  }

  &__btn {
    height: 88rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 31rpx;
    font-weight: 500;
    color: #fff;
    background: $ys-brand-light;
    border-radius: 44rpx;

    &--off {
      opacity: 0.5;
    }
  }
}
</style>
