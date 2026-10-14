<template>
  <div class="app-container">
    <!-- 查询条件：会员 / 卡号模糊 / 卡类型 / 适用门店（年卡无适用门店，不会被命中） -->
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch">
      <el-form-item label="会员" prop="memberId">
        <el-select v-model="queryParams.memberId" filterable clearable placeholder="全部" style="width: 210px">
          <el-option v-for="item in memberOptions" :key="item.id" :label="memberLabel(item)" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="卡号" prop="cardNo">
        <el-input
          v-model="queryParams.cardNo"
          placeholder="卡号模糊查询"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="卡类型" prop="cardType">
        <el-select v-model="queryParams.cardType" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="item in CARD_TYPES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="适用门店" prop="storeId">
        <el-select v-model="queryParams.storeId" filterable clearable placeholder="全部" style="width: 190px">
          <el-option v-for="item in storeOptions" :key="item.id" :label="storeLabel(item)" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <!-- 本期不做 RBAC：业务接口只要求登录，不挂 v-hasPermi -->
        <el-button type="primary" plain icon="Plus" @click="handleAdd">发卡</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="cardList">
      <el-table-column label="卡号" prop="cardNo" width="175" />
      <el-table-column label="卡名称" prop="cardName" min-width="130" show-overflow-tooltip />
      <el-table-column label="卡类型" prop="cardTypeText" width="90" align="center" />
      <el-table-column label="适用课种" prop="courseScopeName" width="100" align="center" />
      <el-table-column label="开卡门店" min-width="140" show-overflow-tooltip>
        <template #default="scope">{{ openStoreText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="适用门店" min-width="130" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.storeName || '-' }}</template>
      </el-table-column>
      <el-table-column label="所属会员" prop="memberName" min-width="130" show-overflow-tooltip />
      <el-table-column label="次数" width="90" align="center">
        <template #default="scope">{{ timesText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="有效期" min-width="200">
        <template #default="scope">{{ validText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="scope">
          <el-tag :type="statusType(scope.row)">{{ statusText(scope.row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发卡时间" prop="createTime" width="170" />
      <el-table-column label="操作" width="260" fixed="right" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button
            v-if="statusKey(scope.row) === 'pending'"
            link
            type="success"
            icon="CircleCheck"
            @click="handleActivate(scope.row)"
          >开卡</el-button>
          <el-button
            v-if="statusKey(scope.row) !== 'stopped'"
            link
            type="warning"
            icon="VideoPause"
            @click="handleStop(scope.row)"
          >停卡</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 发卡：创建待开卡记录，有效期与卡号都在开卡时由服务端生成 -->
    <el-dialog title="会员发卡" v-model="open" width="640px" append-to-body>
      <el-form ref="cardRef" :model="form" :rules="rules" label-width="105px">
        <el-form-item label="所属会员" prop="memberId">
          <el-select v-model="form.memberId" filterable placeholder="请选择会员" style="width: 100%">
            <el-option v-for="item in memberOptions" :key="item.id" :label="memberLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开卡门店" prop="openStoreId">
          <el-select
            v-model="form.openStoreId"
            filterable
            placeholder="请选择开卡门店（卡号中的门店编号取自它）"
            style="width: 100%"
          >
            <el-option v-for="item in storeOptions" :key="item.id" :label="storeLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="卡名称" prop="cardName">
          <el-input v-model="form.cardName" placeholder="如：普拉提次卡 / 瑜伽年卡" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="卡类型" prop="cardType">
          <el-radio-group v-model="form.cardType" @change="handleCardTypeChange">
            <el-radio v-for="item in CARD_TYPES" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="适用课种" prop="courseScope">
          <el-select v-model="form.courseScope" placeholder="请选择适用课种" style="width: 100%">
            <el-option v-for="item in COURSE_SCOPES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <!-- 次数卡专属字段：年卡不提交这几项，后端会拒绝非空 -->
        <template v-if="form.cardType === 1">
          <el-form-item label="适用门店" prop="storeId">
            <el-select
              v-model="form.storeId"
              filterable
              placeholder="次数卡必填（可与开卡门店不同）"
              style="width: 100%"
            >
              <el-option v-for="item in storeOptions" :key="item.id" :label="storeLabel(item)" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="购买次数" prop="purchaseTimes">
            <el-input-number v-model="form.purchaseTimes" :min="0" :max="9999" />
          </el-form-item>
          <el-form-item label="赠送次数" prop="giftTimes">
            <el-input-number v-model="form.giftTimes" :min="0" :max="9999" />
          </el-form-item>
          <el-form-item label="有效期" prop="validPeriodCount">
            <el-select v-model="form.validPeriodCount" placeholder="请选择有效期（月）" style="width: 100%">
              <el-option v-for="item in VALID_PERIODS" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确认发卡</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 停卡：待开卡与使用中均可停，剩余次数与有效期保留 -->
    <el-dialog title="会员卡停卡" v-model="stopOpen" width="520px" append-to-body>
      <el-form ref="stopRef" :model="stopForm" :rules="stopRules" label-width="90px">
        <el-form-item label="会员卡">{{ currentRow.cardName }}（{{ currentRow.cardNo }}）</el-form-item>
        <el-form-item label="所属会员">{{ currentRow.memberName }}</el-form-item>
        <el-form-item label="停卡原因" prop="stopReason">
          <el-input
            v-model="stopForm.stopReason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="必填，例如：客户申请停卡"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="danger" @click="submitStop">确认停卡</el-button>
          <el-button @click="stopOpen = false">返 回</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="会员卡详情" v-model="viewOpen" width="720px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="卡号">{{ detail.cardNo }}</el-descriptions-item>
        <el-descriptions-item label="卡名称">{{ detail.cardName }}</el-descriptions-item>
        <el-descriptions-item label="卡类型">{{ detail.cardTypeText }}</el-descriptions-item>
        <el-descriptions-item label="适用课种">{{ detail.courseScopeName }}</el-descriptions-item>
        <el-descriptions-item label="开卡门店">{{ openStoreText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="适用门店">{{ detail.storeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属会员">{{ detail.memberName }}</el-descriptions-item>
        <el-descriptions-item label="次数">{{ timesText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="有效期" :span="2">{{ validText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detail)">{{ statusText(detail) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发卡时间">{{ detail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="开卡时间">{{ detail.activateTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="停卡时间">{{ detail.stopTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="停卡原因" :span="2">{{ detail.stopReason || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="viewOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="MemberCard">
import {
  listMemberCard,
  getMemberCard,
  addMemberCard,
  activateMemberCard,
  stopMemberCard,
  delMemberCard
} from '@/api/membercard/membercard'
import { listMember } from '@/api/member/member'
import { listStore } from '@/api/store/store'

const { proxy } = getCurrentInstance()

// 卡类型、适用课种是后端固定枚举（不建字典），与 cardTypeText / courseScopeName 取值一致
const CARD_TYPES = [
  { value: 1, label: '次数卡' },
  { value: 2, label: '年卡' }
]
const COURSE_SCOPES = [
  { value: 1, label: '团课类' },
  { value: 2, label: '私教课' }
]
const VALID_PERIODS = [
  { value: 3, label: '3 个月' },
  { value: 6, label: '6 个月' },
  { value: 12, label: '12 个月' }
]

// 状态不由后端返回字段，按「停卡时间 → 开卡时间 → 有效期止 → 剩余次数」派生，
// 优先级：已停卡 > 待开卡 > 已过期 > 次数耗尽 > 使用中
const CARD_STATUSES = {
  stopped: { text: '已停卡', tag: 'danger' },
  pending: { text: '待开卡', tag: 'info' },
  expired: { text: '已过期', tag: 'warning' },
  exhausted: { text: '次数耗尽', tag: 'warning' },
  active: { text: '使用中', tag: 'success' }
}

const cardList = ref([])
const memberOptions = ref([])
const storeOptions = ref([])
const detail = ref({})
const currentRow = ref({})
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const open = ref(false)
const stopOpen = ref(false)
const viewOpen = ref(false)

const data = reactive({
  form: {},
  stopForm: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    memberId: undefined,
    cardNo: undefined,
    cardType: undefined,
    storeId: undefined
  }
})

const { form, stopForm, queryParams } = toRefs(data)

/** 适用门店、购买次数、赠送次数与有效期仅次数卡提交且必填 */
const rules = computed(() => {
  const base = {
    memberId: [{ required: true, message: '请选择所属会员', trigger: 'change' }],
    openStoreId: [{ required: true, message: '请选择开卡门店', trigger: 'change' }],
    cardName: [{ required: true, message: '请输入卡名称', trigger: 'blur' }],
    cardType: [{ required: true, message: '请选择卡类型', trigger: 'change' }],
    courseScope: [{ required: true, message: '请选择适用课种', trigger: 'change' }]
  }
  if (form.value.cardType === 1) {
    base.storeId = [{ required: true, message: '请选择适用门店', trigger: 'change' }]
    base.purchaseTimes = [
      { required: true, message: '请输入购买次数', trigger: 'blur' },
      { validator: validateTimes, trigger: 'change' }
    ]
    base.giftTimes = [
      { required: true, message: '请输入赠送次数', trigger: 'blur' },
      { validator: validateTimes, trigger: 'change' }
    ]
    base.validPeriodCount = [{ required: true, message: '请选择有效期', trigger: 'change' }]
  }
  return base
})

const stopRules = {
  stopReason: [{ required: true, message: '停卡原因不能为空', trigger: 'blur' }]
}

/** 购买次数与赠送次数各自 ≥ 0，合计必须大于 0 */
function validateTimes(rule, value, callback) {
  const total = Number(form.value.purchaseTimes || 0) + Number(form.value.giftTimes || 0)
  if (total <= 0) {
    callback(new Error('购买次数与赠送次数合计必须大于 0'))
  } else {
    callback()
  }
}

function memberLabel(member) {
  return member.phone ? `${member.name}（${member.phone}）` : member.name
}

function storeLabel(store) {
  return `${store.storeNo} ${store.name}`
}

function openStoreText(row) {
  return [row.openStoreNo, row.openStoreName].filter(Boolean).join(' ') || '-'
}

/** 次数卡显示「剩余/总」，年卡不按次数计 */
function timesText(row) {
  return row.cardType === 1 ? `${row.remainingTimes}/${row.totalTimes}` : '-'
}

function dateText(value) {
  return value ? String(value).substring(0, 10) : ''
}

/** 未开卡没有有效期；年卡在有效期内追加剩余天数 */
function validText(row) {
  if (!row.validStartTime) {
    return '-'
  }
  const range = `${dateText(row.validStartTime)} ~ ${dateText(row.validEndTime)}`
  return row.remainingDays === null || row.remainingDays === undefined ? range : `${range}（剩 ${row.remainingDays} 天）`
}

/**
 * 有效期止（yyyy-MM-dd HH:mm:ss，服务端时区）转时间戳；格式异常返回 NaN，此时按未过期处理
 */
function endTimestamp(value) {
  const parts = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})/)
  if (!parts) {
    return NaN
  }
  return new Date(
    Number(parts[1]),
    Number(parts[2]) - 1,
    Number(parts[3]),
    Number(parts[4]),
    Number(parts[5]),
    Number(parts[6])
  ).getTime()
}

/**
 * 有效期已过：年卡以后端算好的剩余天数为准（未过期时必 > 0），次数卡按有效期止比较
 */
function isExpired(row) {
  if (!row.validEndTime) {
    return false
  }
  if (row.cardType === 2) {
    return !(row.remainingDays > 0)
  }
  return Date.now() > endTimestamp(row.validEndTime)
}

/** 次数已用尽：仅次数卡有次数字段 */
function isExhausted(row) {
  if (row.cardType !== 1 || row.remainingTimes === null || row.remainingTimes === undefined) {
    return false
  }
  return Number(row.remainingTimes) <= 0
}

function statusKey(row) {
  if (row.stopTime) {
    return 'stopped'
  }
  if (!row.activateTime) {
    return 'pending'
  }
  if (isExpired(row)) {
    return 'expired'
  }
  return isExhausted(row) ? 'exhausted' : 'active'
}

function statusText(row) {
  return CARD_STATUSES[statusKey(row)].text
}

function statusType(row) {
  return CARD_STATUSES[statusKey(row)].tag
}

/** 加载会员与门店下拉（受分页上限限制，最多展示 100 条） */
function getOptions() {
  listMember({ pageNum: 1, pageSize: 100 }).then(response => {
    memberOptions.value = response.rows || []
  })
  listStore({ pageNum: 1, pageSize: 100 }).then(response => {
    storeOptions.value = response.rows || []
  })
}

/** 查询会员卡列表 */
function getList() {
  loading.value = true
  listMemberCard(queryParams.value).then(response => {
    cardList.value = response.rows || []
    total.value = response.total || 0
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

/** 表单重置 */
function reset() {
  form.value = {
    memberId: undefined,
    openStoreId: undefined,
    cardName: undefined,
    cardType: 1,
    courseScope: 1,
    storeId: undefined,
    purchaseTimes: 0,
    giftTimes: 0,
    validPeriodCount: undefined
  }
  proxy.resetForm('cardRef')
}

/** 发卡按钮操作 */
function handleAdd() {
  reset()
  open.value = true
}

/** 切换为年卡时清空次数卡专属字段，避免提交残留值 */
function handleCardTypeChange(value) {
  if (value === 2) {
    form.value.storeId = undefined
    form.value.purchaseTimes = undefined
    form.value.giftTimes = undefined
    form.value.validPeriodCount = undefined
  } else {
    // 回到次数卡时给次数一个可用初值，避免数字输入框留空
    form.value.purchaseTimes = form.value.purchaseTimes ?? 0
    form.value.giftTimes = form.value.giftTimes ?? 0
  }
}

/** 提交按钮：年卡不提交适用门店、次数与有效期 */
function submitForm() {
  proxy.$refs['cardRef'].validate(valid => {
    if (!valid) {
      return
    }
    const payload = {
      memberId: form.value.memberId,
      openStoreId: form.value.openStoreId,
      cardName: form.value.cardName,
      cardType: form.value.cardType,
      courseScope: form.value.courseScope
    }
    if (form.value.cardType === 1) {
      payload.storeId = form.value.storeId
      payload.purchaseTimes = form.value.purchaseTimes
      payload.giftTimes = form.value.giftTimes
      payload.validPeriodCount = form.value.validPeriodCount
    }
    addMemberCard(payload).then(() => {
      proxy.$modal.msgSuccess('发卡成功')
      open.value = false
      getList()
    }).catch(() => {})
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 查看按钮操作 */
function handleView(row) {
  getMemberCard(row.id).then(response => {
    detail.value = response.data || {}
    viewOpen.value = true
  })
}

/** 开卡：待开卡仅能开一次，开卡后由服务端写入有效期 */
function handleActivate(row) {
  proxy.$modal.confirm('是否确认对会员卡"' + row.cardNo + '"办理开卡？').then(() => {
    return activateMemberCard(row.id)
  }).then(() => {
    proxy.$modal.msgSuccess('开卡成功')
    getList()
  }).catch(() => {})
}

/** 停卡按钮操作 */
function handleStop(row) {
  currentRow.value = row
  stopForm.value = { stopReason: undefined }
  proxy.resetForm('stopRef')
  stopOpen.value = true
}

/** 提交停卡 */
function submitStop() {
  proxy.$refs['stopRef'].validate(valid => {
    if (!valid) {
      return
    }
    stopMemberCard(currentRow.value.id, { stopReason: stopForm.value.stopReason }).then(() => {
      proxy.$modal.msgSuccess('停卡成功')
      stopOpen.value = false
      getList()
    }).catch(() => {})
  })
}

/** 删除按钮操作：失败提示由 axios 拦截器统一弹出 */
function handleDelete(row) {
  proxy.$modal.confirm('是否确认删除会员卡"' + row.cardNo + '"？').then(() => {
    return delMemberCard(row.id)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
  }).catch(() => {})
}

getOptions()
getList()
</script>
