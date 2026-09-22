<template>
  <view class="apply">
    <!-- ===== 表单区 =====
         实测（源图 二级页面-申请体验.png，1170×2532 @3x → 逻辑 390×844）：
         导航栏下沿 y=91pt；分隔线 y = 167 / 223.3 / 279.7 / 352pt（1px #E5E5E5，左右内缩 14.7pt）；
         → 前三行各 56.3pt（108rpx），末行「留言」72.3pt（139rpx）；
         行内左右内边距 14.7pt（28rpx）；标签列右对齐、右内边距 10pt，值列从 84.7pt 起（= 14.7 + 70pt 标签列）；
         首行文字中心 139.3pt → 行顶 111.7pt，即表单上方还有约 20.7pt（40rpx）留白。 -->
    <view class="form">
      <view class="form__top" />

      <!-- 第 1 行：选择门店（必填，右侧 ▲；原型里是原生 select，这里用动作面板换店） -->
      <view class="field press" @tap="onPickStore">
        <view class="field__label">
          <text>选择门店</text>
          <text class="field__star">*</text>
        </view>
        <text class="field__value ys-ellipsis">{{ storeName }}</text>
        <view class="field__caret" />
      </view>
      <view class="form__line" />

      <!-- 第 2 行：姓名（原型未标必填星；左侧是「证件」线性图标，小程序里用 CSS 画同形） -->
      <view class="field">
        <view class="field__label">
          <view class="idcard">
            <view class="idcard__line idcard__line--1" />
            <view class="idcard__line idcard__line--2" />
            <view class="idcard__line idcard__line--3" />
            <view class="idcard__head" />
            <view class="idcard__body" />
          </view>
          <text>姓名</text>
        </view>
        <input
          v-model="form.contactName"
          class="field__input"
          type="text"
          maxlength="20"
          placeholder="请输入姓名"
          placeholder-style="color:#AAAAAA"
        />
      </view>
      <view class="form__line" />

      <!-- 第 3 行：手机号码（必填，右侧手机图标） -->
      <view class="field">
        <view class="field__label">
          <text>手机号码</text>
          <text class="field__star">*</text>
        </view>
        <input
          v-model="form.phone"
          class="field__input"
          type="number"
          maxlength="11"
          placeholder="请输入手机号码"
          placeholder-style="color:#AAAAAA"
        />
        <view class="phone-icon">
          <view class="phone-icon__dot" />
        </view>
      </view>
      <view class="form__line" />

      <!-- 第 4 行：留言（多行；行高 18pt，与标签同基线） -->
      <view class="field field--note">
        <view class="field__label field__label--note">
          <text>留言</text>
        </view>
        <textarea
          v-model="form.remark"
          class="field__textarea"
          maxlength="200"
          :auto-height="false"
          placeholder="请留言"
          placeholder-style="color:#AAAAAA"
        />
      </view>
      <view class="form__line" />
    </view>

    <!-- ===== 底部固定操作栏 =====
         实测：按钮 y 760.7..794pt（高 33.3pt）、x 80..374.7pt（宽 294.7pt）、圆角 17pt；
         按钮下方 794..844pt 留白（Home Indicator 安全区 + 16pt）。
         左「我的预约」图标 + 文字（原型 SVG 是 -42° 的票券图标，小程序不支持内联 SVG，用 CSS 同形拼）。 -->
    <view class="bar">
      <view class="bar__row">
        <view class="bar__entry press" @tap="goMyBookings">
          <view class="ticket">
            <view class="ticket__body">
              <view class="ticket__slit" />
              <view class="ticket__slit" />
            </view>
          </view>
          <text class="bar__entry-text">我的预约</text>
        </view>
        <view class="bar__btn press" @tap="onSubmit">立即预约</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { listStores, getCurrentStore } from '@/api/store'
import { submitTrialApply } from '@/api/trial'

/**
 * 申请体验（照 docs/原型/trial-apply.html + 截图 二级页面-申请体验.png）
 *
 * ⚠️ 页面只有 4 行：选择门店* / 姓名 / 手机号码* / 留言 —— 与截图逐行一致。
 *    任务书里曾提到「性别 / 期望日期 / 期望时段 / 了解渠道」，**原型与截图里都没有这些字段**，
 *    所以没有实现（`getTrialFormOptions()` 仍可用，将来原型补字段时再加行即可）。
 *
 * 必填星只标在「选择门店」「手机号码」上（照截图），校验也按这个来：手机号必填 + 基本格式。
 *
 * 状态栏 / 导航栏 / TabBar 由小程序绘制，本页不画（标题「申请体验」已在 pages.json）。
 * 数据全部来自 @/api/*（内部读 mock），不发任何网络请求。
 */

const form = ref({
  storeId: 0,
  contactName: '',
  phone: '',
  remark: ''
})

const storeName = ref('')

const stores = ref([])

onLoad(async () => {
  const [store, storeList] = await Promise.all([getCurrentStore(), listStores()])
  stores.value = storeList || []
  form.value.storeId = store ? store.id : 0
  storeName.value = store ? store.name : ''
})

/** 原型里「选择门店」是原生 select（只有一个选项）；这里用动作面板等价实现 */
function onPickStore() {
  if (!stores.value.length) {
    return
  }
  uni.showActionSheet({
    itemList: stores.value.map((item) => item.name),
    success: (res) => {
      const picked = stores.value[res.tapIndex]
      if (!picked) {
        return
      }
      form.value.storeId = picked.id
      storeName.value = picked.name
    }
  })
}

/** 手机号：11 位、1 开头（照 utils/validate 的常规口径，这里不引脚手架校验工具） */
const PHONE_RE = /^1[3-9]\d{9}$/

function validate() {
  if (!form.value.phone) {
    uni.showToast({ title: '请输入手机号码', icon: 'none' })
    return false
  }
  if (!PHONE_RE.test(form.value.phone)) {
    uni.showToast({ title: '手机号码格式不正确', icon: 'none' })
    return false
  }
  return true
}

async function onSubmit() {
  if (!validate()) {
    return
  }
  const result = await submitTrialApply({
    storeId: form.value.storeId,
    storeName: storeName.value,
    contactName: form.value.contactName,
    phone: form.value.phone,
    remark: form.value.remark
  })
  if (result && result.success) {
    uni.showToast({ title: result.message || '申请已提交' })
    setTimeout(() => {
      uni.navigateBack()
    }, 600)
    return
  }
  uni.showToast({ title: (result && result.message) || '提交失败，请稍后重试', icon: 'none' })
}

function goMyBookings() {
  // 「我的预约」是 tabBar 页（pages.json 的「已约」），必须用 switchTab
  uni.switchTab({ url: '/pages/my-bookings/index' })
}
</script>

<style lang="scss" scoped>
.apply {
  min-height: 100vh;
  background: #fff;
  /* 给底部固定栏让位：栏内容 44pt + 安全区 + 16pt 余量 */
  padding-bottom: calc(120rpx + env(safe-area-inset-bottom));
}

/* ---------------- 表单 ---------------- */
.form {
  &__top {
    height: 40rpx; /* 首行文字中心 139.3pt 反推：表单上方约 20.7pt 留白 */
  }

  /* 分隔线 1px #E5E5E5，左右内缩 14.7pt（实测分隔线不顶到屏幕边） */
  &__line {
    height: 1px;
    margin: 0 28rpx;
    background: $ys-line-form;
  }
}

.field {
  display: flex;
  align-items: center;
  box-sizing: border-box;
  height: 108rpx; /* 56.3pt */
  padding: 0 28rpx; /* 14.7pt */

  &--note {
    height: 139rpx; /* 72.3pt */
    align-items: flex-start;
  }

  /* 标签列 70pt（135rpx）右对齐 + 右内边距 10pt，值列因此从 84.7pt 起 */
  &__label {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    flex: none;
    box-sizing: border-box;
    width: 135rpx;
    padding-right: 19rpx;
    font-size: 29rpx; /* 15pt */
    line-height: 1;
    color: #080808; /* 实测正文色（token 里没有 #080808，就近的 $ys-ink-1 是 #333） */

    &--note {
      padding-top: 22rpx; /* 留言行：标签与首行文字基线对齐（原型 padding-top 3px + 20px 行高） */
    }
  }

  &__star {
    margin-left: 2rpx;
    color: $ys-star;
  }

  &__value {
    flex: 1;
    min-width: 0;
    font-size: 29rpx;
    line-height: 1;
    color: #080808;
  }

  &__input {
    flex: 1;
    min-width: 0;
    height: 100%;
    font-size: 29rpx;
    color: #080808;
  }

  &__textarea {
    flex: 1;
    min-width: 0;
    box-sizing: border-box;
    height: 108rpx;
    padding-top: 20rpx; /* 与标签首行对齐 */
    font-size: 29rpx;
    line-height: 35rpx; /* 18pt */
    color: #080808;
  }

  /* 右侧 ▲（原型是 6×4pt 上三角，色 #AAAAAA） */
  &__caret {
    flex: none;
    margin-left: 12rpx;
    width: 0;
    height: 0;
    border-left: 6rpx solid transparent;
    border-right: 6rpx solid transparent;
    border-bottom: 8rpx solid $ys-ink-muted;
  }
}

/* 「证件」线性图标（12×10pt → 23×19rpx）：圆角方框 + 左侧三横 + 右侧人形 */
.idcard {
  position: relative;
  flex: none;
  box-sizing: border-box;
  width: 23rpx;
  height: 19rpx;
  margin-right: 12rpx; /* 6pt */
  border: 1px solid $ys-ink-muted;
  border-radius: 3rpx;

  &__line {
    position: absolute;
    left: 4rpx;
    width: 7rpx;
    height: 1px;
    background: $ys-ink-muted;

    &--1 {
      top: 5rpx;
    }

    &--2 {
      top: 9rpx;
    }

    &--3 {
      top: 13rpx;
    }
  }

  &__head {
    position: absolute;
    top: 4rpx;
    right: 4rpx;
    width: 5rpx;
    height: 5rpx;
    border-radius: 50%;
    background: $ys-ink-muted;
  }

  &__body {
    position: absolute;
    right: 3rpx;
    bottom: 3rpx;
    width: 7rpx;
    height: 4rpx;
    border-radius: 4rpx 4rpx 0 0;
    background: $ys-ink-muted;
  }
}

/* 右侧手机图标（9×13pt → 17×25rpx）：圆角矩形 + 下方圆点 */
.phone-icon {
  position: relative;
  flex: none;
  box-sizing: border-box;
  width: 17rpx;
  height: 25rpx;
  margin-left: 12rpx;
  border: 1px solid $ys-ink-muted;
  border-radius: 3rpx;

  &__dot {
    position: absolute;
    left: 50%;
    bottom: 2rpx;
    margin-left: -2rpx;
    width: 4rpx;
    height: 4rpx;
    border-radius: 50%;
    background: $ys-ink-muted;
  }
}

/* ---------------- 底部固定操作栏 ---------------- */
.bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  background: #fff;
  border-top: 1px solid #f6f6f6; /* 原型实测上边线 #F6F6F6 */
  padding-bottom: constant(safe-area-inset-bottom);
  padding-bottom: env(safe-area-inset-bottom);

  &__row {
    display: flex;
    align-items: center;
    box-sizing: border-box;
    height: 85rpx; /* 44pt */
    padding: 0 29rpx; /* 15pt */
  }

  &__entry {
    display: flex;
    flex: none;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 108rpx; /* 56pt */
  }

  &__entry-text {
    margin-top: 8rpx; /* 4pt */
    font-size: 19rpx; /* 10pt */
    line-height: 1;
    color: $ys-ink-muted;
  }

  /* 按钮 294.7×33.3pt（实测；原型 HTML 写 284×34） */
  &__btn {
    display: flex;
    align-items: center;
    justify-content: center;
    flex: none;
    margin-left: auto;
    width: 567rpx; /* 294.7pt */
    height: 65rpx; /* 33.8pt */
    border-radius: 33rpx; /* 17pt */
    font-size: 33rpx; /* 17pt */
    font-weight: 500;
    color: #fff;
    background: $ys-brand-light;
  }
}

/* 「我的预约」票券图标：整体 -42°，灰底圆角方 + 两条白色斜缝（照原型 SVG 结构） */
.ticket {
  width: 31rpx;
  height: 31rpx;
  transform: rotate(-42deg);

  &__body {
    position: relative;
    box-sizing: border-box;
    width: 23rpx;
    height: 23rpx;
    margin: 4rpx;
    border-radius: 5rpx;
    background: $ys-ink-muted;
  }

  &__slit {
    position: absolute;
    top: 3rpx;
    width: 3rpx;
    height: 17rpx;
    border-radius: 2rpx;
    background: #fff;
    transform: rotate(16deg);

    &:first-child {
      left: 6rpx;
    }

    &:last-child {
      left: 13rpx;
    }
  }
}
</style>
