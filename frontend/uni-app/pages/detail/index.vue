<template>
  <!-- 课程详情页：路由参数 scheduleId，数据来自 GET /api/schedules/{scheduleId}；状态文案与 bookable、queueable 用后端返回值 -->
  <view class="detail">
    <view v-if="detail" class="detail__body">
      <view class="hero">
        <image class="hero__cover" :src="coverOf(detail)" mode="aspectFill" />
        <view class="hero__back press" :style="{ top: backTop }" @tap="goBack">
          <uni-icons type="left" size="20" color="#fff" />
        </view>
      </view>

      <!-- 课程信息 -->
      <view class="info">
        <text class="info__tag">课程预约</text>
        <text class="info__title">{{ detail.courseName || '课程' }}</text>

        <view class="grid">
          <view class="grid__cell grid__cell--left">
            <view class="grid__icon"><uni-icons type="calendar" size="17" color="#609CE9" /></view>
            <view class="grid__main">
              <text class="grid__label">日期</text>
              <text class="grid__value">{{ dateText }}</text>
            </view>
          </view>
          <view class="grid__cell">
            <view class="grid__icon"><uni-icons type="notification" size="17" color="#609CE9" /></view>
            <view class="grid__main">
              <text class="grid__label">时间</text>
              <text class="grid__value">{{ timeText }}</text>
            </view>
          </view>
          <view class="grid__cell grid__cell--left">
            <view class="grid__icon"><uni-icons type="info" size="17" color="#609CE9" /></view>
            <view class="grid__main">
              <text class="grid__label">课程时长</text>
              <text class="grid__value">{{ durationText }}</text>
            </view>
          </view>
          <view class="grid__cell">
            <view class="grid__icon grid__icon--star"><uni-icons type="star-filled" size="17" color="#C8963E" /></view>
            <view class="grid__main">
              <text class="grid__label">课程难度</text>
              <text class="grid__value">{{ difficultyText }}</text>
            </view>
          </view>
        </view>

        <view class="place">
          <view class="grid__icon"><uni-icons type="shop" size="17" color="#609CE9" /></view>
          <view class="grid__main">
            <text class="grid__label">上课地点</text>
            <text class="place__store">{{ detail.storeName || '门店待定' }}</text>
            <text class="place__line">{{ roomLine }}</text>
            <text v-if="detail.storeAddress" class="place__line">{{ detail.storeAddress }}</text>
          </view>
        </view>
      </view>

      <!-- 授课教练 -->
      <view class="block">
        <text class="block__title">授课教练</text>
        <view class="coach press" @tap="goCoach">
          <image class="coach__avatar" :src="detail.coachAvatarUrl || DEFAULT_AVATAR" mode="aspectFill" />
          <view class="coach__main">
            <text class="coach__name">{{ detail.coachName || '教练待定' }}</text>
            <text class="coach__intro">{{ detail.coachIntro || '暂无教练简介' }}</text>
          </view>
          <uni-icons v-if="detail.coachId" type="right" size="16" color="#BBBBBB" />
        </view>
      </view>

      <!-- 预约情况 -->
      <view class="block">
        <view class="seats__head">
          <view>
            <text class="block__title block__title--plain">预约情况</text>
            <text class="seats__sub">本节课程席位</text>
          </view>
          <text class="seats__badge">余 {{ remaining }} 席</text>
        </view>

        <view class="seats__nums">
          <view class="seats__num">
            <text class="seats__count seats__count--brand">{{ booked }}</text>
            <text class="seats__caption">已约人数</text>
          </view>
          <view class="seats__divider" />
          <view class="seats__num seats__num--right">
            <text class="seats__count">{{ maxPersons }}</text>
            <text class="seats__caption">课程总人数</text>
          </view>
        </view>

        <view class="seats__bar"><view class="seats__fill" :style="{ width: percent + '%' }" /></view>
        <view class="seats__foot">
          <text>已预约 {{ booked }} 人</text>
          <text>{{ percent }}%</text>
        </view>

        <view class="members">
          <view class="members__main">
            <text class="members__title">参与成员</text>
            <text class="members__sub">{{ booked ? '已有 ' + booked + ' 人加入' : '暂无成员加入' }}</text>
          </view>
          <view v-if="booked" class="members__avatars">
            <image
              v-for="index in shownAvatars"
              :key="index"
              class="members__avatar"
              :src="DEFAULT_AVATAR"
              mode="aspectFill"
            />
            <view v-if="moreCount > 0" class="members__more">
              <text>+{{ moreCount }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 课程介绍 -->
      <view class="block">
        <text class="block__title">课程介绍</text>
        <text class="block__text">{{ detail.courseIntro || '暂无课程介绍' }}</text>
      </view>

      <!-- 预约须知 -->
      <view class="block">
        <text class="block__title">预约须知</text>
        <view class="notes">
          <text v-for="(line, index) in bookingNotes" :key="index" class="notes__item">{{ index + 1 }}.{{ line }}</text>
        </view>
      </view>

      <!-- 排队须知：排课不开放排队时不显示 -->
      <view v-if="queueOpen" class="block">
        <text class="block__title">排队须知</text>
        <view class="notes">
          <text v-for="(line, index) in queueNotes" :key="index" class="notes__item">{{ index + 1 }}.{{ line }}</text>
        </view>
      </view>
    </view>

    <!-- 排课不存在 / 已删除 / 待上架（后端统一 404）→ 友好提示并返回上一页 -->
    <view v-else-if="loadError" class="detail__error" :style="{ paddingTop: backTop }">
      <ys-empty
        image="/static/images/empty/booking.png"
        image-width="265rpx"
        image-height="108rpx"
        title="课程不存在或已下架"
        subtitle="即将返回上一页"
        :padding-top="120"
      />
      <view class="detail__error-btn press" @tap="goBack">
        <text>返回</text>
      </view>
    </view>

    <!-- 底部操作条 -->
    <view v-if="detail" class="bar">
      <view class="bar__consult press" @tap="onConsult">
        <uni-icons type="headphones" size="20" color="#609CE9" />
        <text class="bar__consult-text">咨询</text>
      </view>
      <view class="bar__btn" :class="{ 'bar__btn--off': !canEnter }" @tap="onBook">
        <text>{{ barText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { getScheduleDetail } from '@/api/schedule'

/** 会员与未配头像的教练都用默认头像 */
const DEFAULT_AVATAR = '/static/images/mine/avatar.jpg'
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
const DIFFICULTY_LABELS = ['', '入门', '初级', '适中', '进阶', '高阶']
const MAX_AVATARS = 4

const scheduleId = ref('')
const detail = ref(null)
const loadError = ref(false)

const windowInfo = (typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()) || {}
const backTop = (windowInfo.statusBarHeight || 0) + 12 + 'px'

let backTimer = null

const booked = computed(() => toInt(detail.value && detail.value.bookedPersons))
const maxPersons = computed(() => toInt(detail.value && detail.value.maxPersons))
const remaining = computed(() => toInt(detail.value && detail.value.remainingPlaces))
const percent = computed(() => (maxPersons.value ? Math.min(100, Math.round((booked.value * 100) / maxPersons.value)) : 0))
const shownAvatars = computed(() => Math.min(MAX_AVATARS, booked.value))
const moreCount = computed(() => Math.max(booked.value - shownAvatars.value, 0))

const dateText = computed(() => {
  const date = parseDate(detail.value && (detail.value.scheduleDate || detail.value.startTime))
  return date ? date.getMonth() + 1 + '月' + date.getDate() + '日 ' + WEEKDAYS[date.getDay()] : '日期待定'
})

const timeText = computed(() => {
  const start = hm(detail.value && detail.value.startTime)
  const end = hm(detail.value && detail.value.endTime)
  return start && end ? start + ' - ' + end : start || end || '时间待定'
})

const durationText = computed(() => {
  const start = parseDateTime(detail.value && detail.value.startTime)
  const end = parseDateTime(detail.value && detail.value.endTime)
  if (!start || !end || end <= start) {
    return '时长待定'
  }
  return Math.round((end - start) / 60000) + ' 分钟'
})

const difficultyText = computed(() => {
  const level = toInt(detail.value && detail.value.difficulty)
  return DIFFICULTY_LABELS[level] ? level + ' 星 · ' + DIFFICULTY_LABELS[level] : '暂无'
})

const roomLine = computed(() => {
  const parts = [detail.value.classroomName || '教室待定']
  if (detail.value.storeBusinessHours) {
    parts.push('营业 ' + detail.value.storeBusinessHours)
  }
  return parts.join('　')
})

const queueOpen = computed(() => toInt(detail.value && detail.value.queueLimit) > 0)

const bookingNotes = computed(() => {
  const cancelMinutes = toInt(detail.value.cancelDeadlineMinutes)
  return [
    '本课程需在开课前预约。',
    '预约成功后自动扣除相应费用。',
    '开课前' + cancelMinutes + '分钟不可取消。',
    '取消预约不扣除费用。',
    '该课程满' + toInt(detail.value.minPersons) + '人开课，如预约人数不足会在开课前' + cancelMinutes + '分钟自动取消预约。'
  ]
})

const queueNotes = computed(() => {
  const stopMinutes = toInt(detail.value.queueStopMinutes)
  return [
    '本课程需早于开课前' + stopMinutes + '分钟排队。',
    '开课前' + stopMinutes + '分钟停止从排队中加入预约。',
    '排队上限为' + toInt(detail.value.queueLimit) + '人。',
    '排队时不扣费，从排队转为预约成功后扣除相应费用。'
  ]
})

function toInt(value) {
  const number = Number(value)
  return Number.isFinite(number) ? Math.max(Math.floor(number), 0) : 0
}

function hm(value) {
  const text = String(value || '')
  return text.length >= 16 ? text.slice(11, 16) : text
}

function parseDate(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(value || ''))
  return match ? new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3])) : null
}

function parseDateTime(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/.exec(String(value || ''))
  return match
    ? new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]), Number(match[4]), Number(match[5]))
    : null
}

/** 后端未配封面时用原型封面占位 */
function coverOf(item) {
  return item.courseCoverUrl || '/static/images/booking/cover-1.jpg'
}

onLoad((query) => {
  scheduleId.value = query && query.scheduleId ? String(query.scheduleId) : ''
  loadDetail()
})

onUnload(() => {
  if (backTimer) {
    clearTimeout(backTimer)
    backTimer = null
  }
})

async function loadDetail() {
  if (!scheduleId.value) {
    onNotFound()
    return
  }
  try {
    const data = await getScheduleDetail(scheduleId.value)
    if (!data) {
      onNotFound()
      return
    }
    detail.value = data
  } catch (error) {
    onNotFound()
  }
}

/** 友好提示后自动返回上一页，没有上一页时回约课 Tab */
function onNotFound() {
  loadError.value = true
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

function goCoach() {
  if (!detail.value || !detail.value.coachId) {
    return
  }
  uni.navigateTo({ url: '/pages/coaches/index?coachId=' + encodeURIComponent(detail.value.coachId) })
}

function onConsult() {
  const phone = detail.value && detail.value.storePhone
  if (!phone) {
    uni.showToast({ title: '门店暂未提供电话', icon: 'none' })
    return
  }
  uni.makePhoneCall({ phoneNumber: phone })
}

/** 可约进预约页；满员可排队也进同一页，由该页提交排队 */
const canEnter = computed(() => !!detail.value && (detail.value.bookable || detail.value.queueable))

const barText = computed(() => {
  if (!detail.value) {
    return ''
  }
  if (detail.value.bookable) {
    return '立即预约'
  }
  if (detail.value.queueable) {
    return '排队'
  }
  return queueFull.value ? '排队已满' : detail.value.statusText
})

/** 满员且排队人数已达上限（上限为 0 表示不开放排队，不算排满） */
const queueFull = computed(() => {
  const item = detail.value
  return !!item && item.remainingPlaces === 0 && item.queueLimit > 0 && item.queueCount >= item.queueLimit
})

function onBook() {
  if (!canEnter.value) {
    return
  }
  uni.navigateTo({ url: '/pages/booking-confirm/index?scheduleId=' + scheduleId.value })
}
</script>

<style lang="scss" scoped>
.detail {
  min-height: 100vh;
  background: $ys-page;

  &__body {
    padding-bottom: 180rpx;
  }

  &__error-btn {
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

.hero {
  position: relative;

  &__cover {
    display: block;
    width: 100%;
    height: 560rpx;
    background: $ys-line;
  }

  &__back {
    position: absolute;
    left: 24rpx;
    width: 64rpx;
    height: 64rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.35);
    border-radius: 50%;
  }
}

.info {
  position: relative;
  margin: -24rpx 16rpx 0;
  padding: 36rpx 32rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 6rpx 24rpx rgba(47, 71, 84, 0.08);

  &__tag {
    display: block;
    font-size: 25rpx;
    font-weight: 600;
    color: $ys-brand;
  }

  &__title {
    display: block;
    margin-top: 12rpx;
    font-size: 44rpx;
    font-weight: 700;
    line-height: 1.3;
    color: $ys-ink-1;
  }
}

.grid {
  display: flex;
  flex-wrap: wrap;
  margin-top: 28rpx;
  border-top: 1px solid $ys-line;

  &__cell {
    width: 50%;
    box-sizing: border-box;
    display: flex;
    align-items: center;
    padding: 26rpx 0 26rpx 22rpx;
    border-bottom: 1px solid $ys-line;

    &--left {
      padding-left: 0;
      border-right: 1px solid $ys-line;
    }
  }

  &__icon {
    flex: none;
    width: 60rpx;
    height: 60rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    background: $ys-brand-pale;
    border-radius: 12rpx;

    &--star {
      background: #f8eedc;
    }
  }

  &__main {
    flex: 1;
    min-width: 0;
    margin-left: 22rpx;
    display: flex;
    flex-direction: column;
  }

  &__label {
    font-size: 25rpx;
    line-height: 1.4;
    color: $ys-ink-2;
  }

  &__value {
    margin-top: 6rpx;
    font-size: 29rpx;
    font-weight: 600;
    line-height: 1.4;
    color: $ys-ink-1;
  }
}

.place {
  display: flex;
  align-items: flex-start;
  padding-top: 26rpx;

  &__store {
    margin-top: 6rpx;
    font-size: 29rpx;
    font-weight: 600;
    line-height: 1.4;
    color: $ys-ink-1;
  }

  &__line {
    margin-top: 8rpx;
    font-size: 25rpx;
    line-height: 1.5;
    color: $ys-ink-2;
  }
}

.block {
  margin-top: 20rpx;
  padding: 36rpx 34rpx;
  background: #fff;

  &__title {
    display: block;
    padding-bottom: 26rpx;
    font-size: 31rpx;
    font-weight: 700;
    line-height: 1.3;
    color: $ys-ink-1;
    border-bottom: 1px solid $ys-line;

    &--plain {
      padding-bottom: 0;
      border-bottom: none;
    }
  }

  &__text {
    display: block;
    margin-top: 28rpx;
    font-size: 29rpx;
    line-height: 1.6;
    color: $ys-ink-2;
  }
}

.notes {
  padding-top: 10rpx;

  &__item {
    display: block;
    margin-top: 18rpx;
    font-size: 29rpx;
    line-height: 1.7;
    color: $ys-ink-2;
  }
}

.coach {
  display: flex;
  align-items: center;
  padding-top: 26rpx;

  &__avatar {
    flex: none;
    width: 96rpx;
    height: 96rpx;
    border-radius: 50%;
    background: $ys-line;
  }

  &__main {
    flex: 1;
    min-width: 0;
    margin-left: 24rpx;
    display: flex;
    flex-direction: column;
  }

  &__name {
    font-size: 29rpx;
    font-weight: 600;
    line-height: 1.4;
    color: $ys-ink-1;
  }

  &__intro {
    margin-top: 6rpx;
    font-size: 25rpx;
    line-height: 1.5;
    color: $ys-ink-2;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.seats {
  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
  }

  &__sub {
    display: block;
    margin-top: 10rpx;
    font-size: 25rpx;
    color: $ys-ink-2;
  }

  &__badge {
    padding: 6rpx 16rpx;
    font-size: 25rpx;
    font-weight: 600;
    color: $ys-brand-deep;
    background: $ys-brand-pale;
    border-radius: 8rpx;
  }

  &__nums {
    display: flex;
    align-items: center;
    margin-top: 36rpx;
  }

  &__num {
    flex: 1;
    display: flex;
    flex-direction: column;

    &--right {
      align-items: flex-end;
    }
  }

  &__divider {
    width: 1px;
    height: 64rpx;
    background: $ys-line;
  }

  &__count {
    font-size: 52rpx;
    font-weight: 700;
    line-height: 1.2;
    color: $ys-ink-1;

    &--brand {
      color: $ys-brand-deep;
    }
  }

  &__caption {
    margin-top: 6rpx;
    font-size: 25rpx;
    color: $ys-ink-2;
  }

  &__bar {
    margin-top: 32rpx;
    height: 12rpx;
    background: $ys-line;
    border-radius: 12rpx;
    overflow: hidden;
  }

  &__fill {
    height: 100%;
    background: linear-gradient(90deg, $ys-brand-deep, $ys-brand-light);
    border-radius: 12rpx;
  }

  &__foot {
    display: flex;
    justify-content: space-between;
    margin-top: 14rpx;
    font-size: 25rpx;
    color: $ys-ink-2;
  }
}

.members {
  display: flex;
  align-items: center;
  margin-top: 30rpx;
  padding-top: 30rpx;
  border-top: 1px solid $ys-line;

  &__main {
    flex: 1;
    display: flex;
    flex-direction: column;
  }

  &__title {
    font-size: 27rpx;
    font-weight: 600;
    color: $ys-ink-1;
  }

  &__sub {
    margin-top: 8rpx;
    font-size: 25rpx;
    color: $ys-ink-2;
  }

  &__avatars {
    display: flex;
    align-items: center;
  }

  &__avatar,
  &__more {
    width: 60rpx;
    height: 60rpx;
    margin-left: -14rpx;
    border: 3rpx solid #fff;
    border-radius: 50%;
    background: $ys-line;
  }

  &__more {
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 20rpx;
    color: $ys-ink-2;
    background: $ys-brand-pale;
  }
}

.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  padding: 20rpx 25rpx calc(20rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.05);

  &__consult {
    flex: none;
    width: 112rpx;
    height: 88rpx;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    background: $ys-brand-pale;
    border-radius: 12rpx;
  }

  &__consult-text {
    margin-top: 2rpx;
    font-size: 23rpx;
    color: $ys-ink-1;
  }

  &__btn {
    flex: 1;
    margin-left: 20rpx;
    height: 88rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 31rpx;
    font-weight: 500;
    color: #fff;
    background: $ys-brand;
    border-radius: 12rpx;

    &--off {
      color: $ys-ink-3;
      background: #eef1f5;
    }
  }
}
</style>
