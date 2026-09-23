<template>
  <!--
    约课页（严格照 docs/原型/booking.html）

    状态栏 / 导航栏 / 微信胶囊 / TabBar 由小程序绘制，本页不画。
    导航栏标题「一水瑜伽」已在 pages.json 配好；原型导航栏左侧的返回箭头是一级页上的
    历史残留，按系统导航栏处理，不自己画。
  -->
  <view class="booking">
    <!-- 顶部浅蓝渐隐带画在页面根节点 .booking 上（0 → 356rpx 由 #BAE6FC 渐隐到页面底色 #F7F7F7，
         原型实测：0..90pt 纯色、90..275pt 渐隐）。这里只负责排 Tab。 -->
    <view class="booking__top">
      <!-- 品类 Tab：团课 / 精品课 / 私教课 / 特色课
           选中态 = 文字加深加粗（z-index 压在蓝条上）+ 其下一条 50.7×8pt 饱和蓝胶囊（衬在文字下部） -->
      <view class="tabs">
        <view
          v-for="type in courseTypes"
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

    <!-- 日期条：白色圆角卡 + 横向可滑动（7 天：9.20 日 ~ 9.26 六，选中 9.20） -->
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
            <!-- 选中态两件事都做：pill 变「#609CE9 蓝底 + 白字」，下方再加白底圆内蓝色对勾；
                 未选中态：深灰文字 + 下方蓝色小圆点 -->
            <view v-if="day.date === activeDate" class="day-col__check">
              <uni-icons type="checkmarkempty" size="11" color="#609CE9" />
            </view>
            <view v-else class="day-col__dot" />
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 场次列表：数据来自 listSchedules({ date, type })
         卡片样式严格照 docs/原型/booking.html 的 .ccard（实测值，见下方 style 注释）；
         原型这里用的是 `ys-course-card` 之外的专门样式，所以本页不再复用该组件。 -->
    <view v-if="schedules.length" class="schedules">
      <view
        v-for="item in schedules"
        :key="item.id"
        class="ccard press"
        @tap="onCourseTap(item)"
      >
        <!-- 封面：原型这 3 张卡的封面就是从截图裁下来的照片、与课程本身无关，此处只作占位 -->
        <image class="ccard__photo" :src="coverOf(item)" mode="aspectFill" />

        <text class="ccard__title">{{ item.courseName }}</text>

        <!-- 描述：原型这里是占位文案「暂无描述」，我们用真实 mock 字段拼：
             「教练名 · 起始时间 · 剩余 N」（已满则显示「已满」） -->
        <text class="ccard__desc">{{ descOf(item) }}</text>

        <view
          class="ccard__btn"
          :class="{ 'ccard__btn--off': isSoldOut(item) }"
          @tap.stop="onBook(item)"
        >
          <text>{{ isSoldOut(item) ? '已满' : '立即预约' }}</text>
        </view>
      </view>
    </view>
    <view v-else class="booking__empty">
      <ys-empty title="当天暂无可约课程" />
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getCourseTypes, getBookingDates, listSchedules, bookSchedule } from '@/api/course'

/**
 * 约课页
 *
 * 数据全部来自 @/api/course（内部读 mock），不发任何网络请求。
 * 品类 / 日期任一变化都重新调用 listSchedules。
 */

const courseTypes = ref([])
const dates = ref([])
const schedules = ref([])

/** 原型约课页选中的是「私教课」+ 9.20 */
const activeType = ref(3)
const activeDate = ref('')

/** 原型 BOOKING_DATES 给的是「周日 / 09-20」，日期条上要显示「日」「9.20」 */
function toStripDay(item) {
  return Object.assign({}, item, {
    weekShort: (item.week || '').replace('周', ''),
    dateShort: (item.label || '').replace('-', '.').replace(/^0/, '')
  })
}

async function loadSchedules() {
  schedules.value = await listSchedules({ date: activeDate.value, type: activeType.value })
}

async function init() {
  const [types, days] = await Promise.all([getCourseTypes(), getBookingDates()])
  courseTypes.value = types
  dates.value = days.map(toStripDay)
  if (!activeDate.value && dates.value.length) {
    activeDate.value = dates.value[0].date
  }
  await loadSchedules()
}

onShow(() => {
  init()
})

function onTypeChange(value) {
  if (value === activeType.value) {
    return
  }
  activeType.value = value
  loadSchedules()
}

function onDateChange(date) {
  if (date === activeDate.value) {
    return
  }
  activeDate.value = date
  loadSchedules()
}

/* ---------------- 场次卡（原型 .ccard） ---------------- */

/** 名额已满（api 的 toScheduleCard 已算好 soldOut） */
function isSoldOut(item) {
  return !!item.soldOut
}

/**
 * 卡片描述：原型写的是占位文案「暂无描述」，
 * 这里按 task 要求换成真实 mock 字段拼装：「教练名 · 起始时间 · 剩余 N」。
 * - 教练名为空时跳过该段
 * - 已满时最后一段显示「已满」
 */
function descOf(item) {
  const parts = []
  if (item.coachName) {
    parts.push(item.coachName)
  }
  if (item.startTime) {
    parts.push(item.startTime)
  }
  parts.push(isSoldOut(item) ? '已满' : '剩余 ' + item.left)
  return parts.join(' · ')
}

/**
 * 封面占位：优先用接口给的 coverUrl（mock 里目前是空串）；
 * 否则按 courseId 取模在 cover-1 / cover-2 之间轮换。
 * ⚠️ 原型这 3 张卡的封面本来就是从截图里裁下来的照片，与课程本身无关，
 *    所以这里只是「视觉占位」，不是真实课程封面。
 */
function coverOf(item) {
  if (item.coverUrl) {
    return item.coverUrl
  }
  const seed = Number(item.courseId || item.id || 0)
  return seed % 2 === 0 ? '/static/images/booking/cover-1.jpg' : '/static/images/booking/cover-2.jpg'
}

/** 整卡可点：跳课程详情 */
function onCourseTap() {
  // TODO 课程详情页还没有登记进 pages.json（@/api/course 的 getCourseDetail 已可用），
  //      登记后再改成 uni.navigateTo({ url: '/pages/course-detail/index?id=' + item.id })
  uni.showToast({ title: '课程详情页暂未提供', icon: 'none' })
}

async function onBook(item) {
  // 已满的场次不响应
  if (isSoldOut(item)) {
    return
  }
  const res = await bookSchedule(item.id)
  if (res && res.success) {
    uni.showToast({ title: '预约成功' })
    await loadSchedules()
    return
  }
  uni.showToast({ title: (res && res.message) || '预约失败', icon: 'none' })
}
</script>

<style lang="scss" scoped>
.booking {
  min-height: 100vh;
  /*
    顶部浅蓝渐隐带（原型 `.screen` 底色，按截图 x=30 竖向取色实测）：
      0..90pt   纯 #BAE6FC —— 这 90pt 正好是状态栏 + 导航栏，小程序里由系统外壳盖住
      90..275pt 线性渐隐 #BAE6FC → #F7F7F7（页面底色），275pt 之后恒为 #F7F7F7
    小程序的内容从系统导航栏以下开始，所以这里直接从 #BAE6FC 起、渐隐 185pt（=356rpx）落到页面底色。
    ⚠️ 这块渐变必须画在页面根节点上：只给一行高的 `__top` 加渐变，元素底边会硬切出一条蓝色横线
       （元素高约 94rpx，远短于渐变的 285rpx，渐变根本没机会渐隐）。
  */
  background: linear-gradient(180deg, #bae6fc 0rpx, #f7f7f7 356rpx, #f7f7f7 100%);

  /* 头部只负责排 Tab，背景由根节点承担 */
  &__top {
    background: transparent;
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
      .tab-row  { height:40px; padding-top:22px }                  ← 行的内边距/高度由 .tabs 承担
      .tab-hit  { position:relative; display:inline-block;
                  height:22px; line-height:22px }                  → 42rpx 高的 hit 框
      .tab-hi   { position:absolute; left:50%; bottom:3px;
                  transform:translateX(-50%); width:47px; height:8px;
                  border-radius:999px; background:#609AE9 }         → bottom 6rpx / 高 15rpx
      选中文字  class="relative z-10 ... text-[17px] font-semibold text-[#303337] leading-none"
                → 文字必须压在蓝条上面（原型靠 z-10），字号 33rpx
  */
  &__hit {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    height: 42rpx; /* 原型 .tab-hit 的 22pt：蓝条按「框内 bottom:3px」定位，框高不对蓝条位置就不对 */
    line-height: 42rpx;
  }

  &__text {
    font-size: 33rpx;
    line-height: 1;
    color: #71777e;

    &--on {
      position: relative;
      z-index: 10; /* 原型 z-10：文字压在蓝条上，蓝条只是衬底（否则蓝条会盖住笔画） */
      font-weight: 600;
      color: #303337;
    }
  }

  /* 选中态下划线：原型 8pt 高胶囊，位置在 22pt 高 hit 框的下部（bottom 3pt） */
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
/* 列表容器：左右内边距 15pt → 29rpx；日期条卡底到第一张卡顶 23pt → 44rpx；
   卡片间距 16pt → 31rpx；底部内边距 20pt → 38rpx */
.schedules {
  /* 卡片间距用 flex + gap：WXSS 不支持通配符选择器， 写成星号通配符选择器会被微信编译器拒绝 */
  display: flex;
  flex-direction: column;
  gap: 31rpx;
  padding: 44rpx 29rpx 38rpx;
}

/* 场次卡：严格照原型 .ccard 实测值（pt × 1.9231 = rpx）
   卡片高 115pt、圆角 13pt、阴影 0 1px 2px rgba(0,0,0,.05)
   封面 left 15 / top 14 / 85×85 / 圆角 10
   标题 left 135 / top 24 / 15pt 600 #303339
   描述 left 135 / top 50 / 15pt #9A9A9A
   按钮 right 15 / top 71 / 76×29 / 圆角 999 / #609CE9 / 白字 15pt */
.ccard {
  position: relative;
  height: 221rpx;
  background: #fff;
  border-radius: 25rpx;
  box-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.05);

  &__photo {
    position: absolute;
    left: 29rpx;
    top: 27rpx;
    width: 163rpx;
    height: 163rpx;
    border-radius: 19rpx;
    background: $ys-line;
  }

  &__title {
    position: absolute;
    left: 260rpx;
    top: 46rpx;
    font-size: 29rpx;
    font-weight: 600;
    line-height: 1;
    color: #303339;
  }

  &__desc {
    position: absolute;
    left: 260rpx;
    top: 96rpx;
    font-size: 29rpx;
    line-height: 1;
    color: #9a9a9a;
  }

  &__btn {
    position: absolute;
    right: 29rpx;
    top: 137rpx;
    width: 146rpx;
    height: 56rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 29rpx;
    line-height: 1;
    color: #fff;
    background: $ys-brand;
    border-radius: 56rpx;

    /* 名额已满：按钮置灰 */
    &--off {
      background: #c9d3dd;
    }
  }
}
</style>
