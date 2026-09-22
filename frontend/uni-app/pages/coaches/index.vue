<template>
  <view class="coaches">
    <!-- 天空/浅蓝渐变背景（实测：内容区顶 y=0 到 y=301pt 结束，之后是白底）
         色标取自截图纵向采样：顶部 #8AB3E4 → 141pt #8BBAEE → 316pt #C9E4F5 → 392pt #EDF4FA -->
    <view class="coaches__sky" />

    <view class="coaches__body">
      <!-- ===== 教练资料白卡 =====
           实测：卡顶 52.3pt（全屏 143.3pt）、高 179.7pt、左右各 18pt（宽 353.7pt）、圆角 24pt；
           头像为 66×66pt 正圆（全屏 x 162..228 / y 106.3..172.7，水平居中），
           卡顶与头像顶差 37pt → 头像上探 -37pt。
           卡内文案 x 从 45pt 起（卡内左内边距 27pt），字号/行高按截图墨迹实测：
           姓名 16pt/22pt（墨迹 88.7..103.3pt）、简介 14pt/20pt（126.3..139）、
           相册标题 15pt/21pt（160.7..175.3）、相册空态 13pt/19pt #999999（188.7..201）。 -->
      <view class="profile press" @tap="onSwitchCoach">
        <view class="profile__avatar">
          <image v-if="coach.avatar" class="profile__avatar-img" :src="coach.avatar" mode="aspectFill" />
          <!-- 教练 4/5（林溪 / 苏晴）没有头像资产（原型只给了三位教练）→ 灰底 + 姓名首字占位 -->
          <text v-else class="profile__avatar-text">{{ coach.name ? coach.name.slice(0, 1) : '教' }}</text>
        </view>
        <text class="profile__name">{{ coach.name }}</text>
        <text class="profile__intro">{{ coach.intro || '暂无教练简介' }}</text>
        <text class="profile__album-title">教练相册</text>
        <text class="profile__album-empty">暂无教练相册</text>
      </view>

      <!-- ===== 品类 Tab（团课 / 精品课 / 私教课 / 特色课）=====
           实测：文字墨迹 y 254.7..269.3pt（15pt）、下划线 y 277..279.7pt（20×2.6pt、圆角）；
           四个 Tab 文字中心 x = 63.8 / 151 / 238.7 / 326pt（等距 87.5）→ 行内左右各留 20pt，四等分。 -->
      <view class="tabs">
        <view
          v-for="tab in courseTypes"
          :key="tab.value"
          class="tabs__item press"
          @tap="onTypeChange(tab.value)"
        >
          <text class="tabs__text" :class="{ 'tabs__text--on': tab.value === activeType }">
            {{ tab.label }}
          </text>
          <view class="tabs__bar" :class="{ 'tabs__bar--on': tab.value === activeType }" />
        </view>
      </view>

      <!-- ===== 课程卡片 =====
           实测：首卡顶 320pt、卡高 124pt、间距 17pt、左右各 15pt、圆角 16pt、底 #D0E9FF（纯色，非渐变）；
           封面 104×104pt、卡内左 11.7pt、垂直居中；文字列从卡内 135pt 起（封面右缘 + 19.3pt）。 -->
      <view v-if="filteredSchedules.length" class="list">
        <view
          v-for="item in filteredSchedules"
          :key="item.id"
          class="course press"
          @tap="onCourseTap(item)"
        >
          <image class="course__pic" :src="item.coverUrl" mode="aspectFill" />
          <view class="course__info">
            <view class="course__title-row">
              <text v-if="typeLabel(item.courseType)" class="course__badge">{{ typeLabel(item.courseType) }}</text>
              <text class="course__name">{{ item.courseName }}</text>
            </view>
            <text class="course__meta">时间 {{ item.startTime }} - {{ item.endTime }}</text>
            <text class="course__meta">{{ item.room }}</text>
            <text class="course__meta">剩余 {{ item.left }}</text>
          </view>
        </view>
      </view>

      <!-- 该品类下这位教练没有排班时的兜底（⚠️ 原型没有这一态，是按本项目其它空态的写法补的） -->
      <view v-else class="empty">
        <text class="empty__text">暂无数据</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { listCoaches, getCoachDetail } from '@/api/coach'
import { getCourseTypes } from '@/api/course'

/**
 * 约教练（照 docs/原型/coaches.html + 截图 二级页面-约教练.png）
 *
 * 原型这一页**不是教练列表，而是「一位教练的资料卡 + 品类 Tab + 他的课程卡」**：
 * 白卡里是头像 / 姓名 / 暂无教练简介 / 教练相册 / 暂无教练相册，下面是四个品类 Tab，
 * 再下面是该教练的课程卡（封面 + 品类角标 + 课程名 + 时间 / 教室 / 剩余）。
 * 截图里 Tab 选中「团课」、三张卡是「哈它瑜伽 / 流瑜伽 / 核心流瑜伽」——那是原型给的样例数据，
 * 这里按 mock 的真实数据渲染（mock 里这位教练是哈他瑜伽 / 阴瑜伽）。
 *
 * 换教练：原型没有教练列表页，所以**不在页面上发明列表 UI**，
 * 改成点击资料卡 → 原生动作面板选教练（`uni.showActionSheet`），选中后原地换人换课。
 *
 * 数据全部来自 @/api/coach（内部读 mock），不发任何网络请求。
 * 状态栏 / 导航栏 / TabBar 由小程序绘制，本页不画（标题「约教练」已在 pages.json）。
 */

const coachList = ref([])
const coach = ref({})
const courseTypes = ref([])
const schedules = ref([])
const activeType = ref(1) // 原型选中「团课」

const filteredSchedules = computed(() => {
  return schedules.value.filter((item) => Number(item.courseType) === Number(activeType.value))
})

function typeLabel(type) {
  const hit = courseTypes.value.find((item) => Number(item.value) === Number(type))
  return hit ? hit.label : ''
}

async function loadCoach(coachId) {
  const detail = await getCoachDetail(coachId)
  if (!detail) {
    return
  }
  coach.value = detail.coach || {}
  schedules.value = detail.schedules || []
}

onLoad(async () => {
  const [types, list] = await Promise.all([getCourseTypes(), listCoaches()])
  courseTypes.value = types || []
  coachList.value = list || []
  if (coachList.value.length) {
    await loadCoach(coachList.value[0].id)
  }
})

function onTypeChange(value) {
  if (value === activeType.value) {
    return
  }
  activeType.value = value
}

/** 换教练：原型没有教练列表 UI，用原生动作面板，不在页面上发明内容 */
function onSwitchCoach() {
  if (coachList.value.length < 2) {
    return
  }
  uni.showActionSheet({
    itemList: coachList.value.map((item) => item.name),
    success: (res) => {
      const picked = coachList.value[res.tapIndex]
      if (picked && picked.id !== coach.value.id) {
        loadCoach(picked.id)
      }
    }
  })
}

/** 课程详情页不在本轮原型范围内，先只给点击反馈 */
function onCourseTap() {
  uni.showToast({ title: '课程详情页暂未提供', icon: 'none' })
}
</script>

<style lang="scss" scoped>
.coaches {
  position: relative;
  min-height: 100vh;
  background: #fff;
  overflow: hidden; /* 天空渐变只到 301pt，别被内容溢出撑破 */

  &__sky {
    position: absolute;
    left: 0;
    top: 0;
    width: 100%;
    height: 579rpx; /* 301pt */
    background: linear-gradient(
      180deg,
      #8ab3e4 0%,
      #8bbaee 17%,
      #b7d9f2 48%,
      #c9e4f5 75%,
      #e6f0f9 91%,
      #edf4fa 100%
    );
  }

  &__body {
    position: relative;
  }
}

/* ---------------- 教练资料白卡 ---------------- */
.profile {
  position: relative;
  box-sizing: border-box;
  margin: 101rpx 35rpx 0; /* 上 52.3pt、左右 18pt */
  height: 346rpx; /* 179.7pt */
  padding: 65rpx 52rpx 0; /* 姓名行盒顶 33.7pt、左右 27pt */
  background: #fff;
  border-radius: 46rpx; /* 24pt */
  box-shadow: 0 4rpx 23rpx rgba(96, 156, 233, 0.1);

  &__avatar {
    position: absolute;
    left: 50%;
    top: -71rpx; /* 头像顶比卡顶高 37pt */
    margin-left: -63rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 127rpx; /* 66pt */
    height: 127rpx;
    border-radius: 50%;
    overflow: hidden;
    background: $ys-line; /* 无头像资产时的灰底 */
  }

  &__avatar-img {
    width: 100%;
    height: 100%;
  }

  &__avatar-text {
    font-size: 42rpx;
    font-weight: 600;
    color: $ys-ink-3;
  }

  &__name {
    display: block;
    font-size: 31rpx; /* 16pt */
    font-weight: 600;
    line-height: 42rpx; /* 22pt */
    text-align: center;
    color: $ys-ink-1;
  }

  &__intro {
    display: block;
    margin-top: 29rpx; /* 15.3pt */
    font-size: 27rpx; /* 14pt */
    line-height: 38rpx; /* 20pt */
    color: $ys-ink-1;
  }

  &__album-title {
    display: block;
    margin-top: 28rpx; /* 14.4pt */
    font-size: 29rpx; /* 15pt */
    font-weight: 600;
    line-height: 40rpx; /* 21pt */
    color: $ys-ink-1;
  }

  &__album-empty {
    display: block;
    margin-top: 13rpx; /* 7pt */
    font-size: 25rpx; /* 13pt */
    line-height: 37rpx; /* 19pt */
    color: $ys-ink-3;
  }
}

/* ---------------- 品类 Tab ---------------- */
.tabs {
  display: flex;
  margin-top: 36rpx; /* 卡片底 232.3pt → Tab 文字行盒顶 251.2pt */
  padding: 0 38rpx; /* 20pt */

  &__item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  &__text {
    font-size: 29rpx; /* 15pt */
    line-height: 42rpx; /* 22pt */
    color: $ys-ink-1;

    &--on {
      color: $ys-brand;
    }
  }

  &__bar {
    margin-top: 10rpx; /* 5pt */
    width: 38rpx; /* 20pt */
    height: 5rpx; /* 2.6pt */
    border-radius: 4rpx;
    background: transparent;

    &--on {
      background: $ys-brand;
    }
  }
}

/* ---------------- 课程卡片 ---------------- */
.list {
  /* 卡片间距用 flex + gap：WXSS 不支持星号通配符选择器（会报 error at token） */
  display: flex;
  flex-direction: column;
  gap: 33rpx; /* 17pt */
  padding: 0 29rpx; /* 15pt */
  margin-top: 75rpx; /* Tab 下划线底 280.8pt → 首卡顶 320pt */
}

.course {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  height: 238rpx; /* 124pt */
  padding-left: 23rpx; /* 11.7pt */
  background: #d0e9ff; /* 实测纯色（原型写的是 90° 渐变，采样下来左右一致） */
  border-radius: 31rpx; /* 16pt */

  &__pic {
    flex: none;
    width: 200rpx; /* 104pt */
    height: 200rpx;
    border-radius: 23rpx; /* 12pt */
    background: #c7dff5;
  }

  &__info {
    flex: 1;
    min-width: 0;
    margin-left: 37rpx; /* 19.3pt（文字列卡内 135pt） */
  }

  &__title-row {
    display: flex;
    align-items: center;
    height: 46rpx; /* 24pt */
  }

  /* 品类角标：实测底 #C4C4C4（原型写 #C6D3E0）、白字 11pt、圆角 6pt、左右 3pt */
  &__badge {
    flex: none;
    margin-right: 8rpx; /* 4pt */
    padding: 0 6rpx;
    height: 35rpx; /* 18pt */
    border-radius: 12rpx;
    font-size: 21rpx; /* 11pt */
    line-height: 35rpx;
    color: #fff;
    background: #c4c4c4;
  }

  &__name {
    flex: 1;
    min-width: 0;
    font-size: 31rpx; /* 16pt */
    font-weight: 600;
    line-height: 46rpx; /* 24pt */
    color: $ys-ink-1;
  }

  &__meta {
    display: block;
    font-size: 27rpx; /* 14pt */
    line-height: 38rpx; /* 20pt */
    color: $ys-ink-2;
  }
}

/* ---------------- 该品类无排班时的兜底（原型没有这一态） ---------------- */
.empty {
  display: flex;
  justify-content: center;
  padding-top: 120rpx;

  &__text {
    font-size: 27rpx;
    color: $ys-ink-muted;
  }
}
</style>
