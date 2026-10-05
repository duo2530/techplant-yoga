<template>
  <div class="app-container">
    <el-form
      ref="queryRef"
      v-show="showSearch"
      :model="queryParams"
      :inline="true"
    >
      <el-form-item label="会员" prop="memberId">
        <el-select v-model="queryParams.memberId" filterable clearable placeholder="全部" style="width: 210px">
          <el-option
            v-for="item in memberOptions"
            :key="item.id"
            :label="`${item.name}（${item.phone}）`"
            :value="item.id"
          />
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
      <el-form-item label="卡片类型" prop="cardType">
        <el-select v-model="queryParams.cardType" placeholder="全部" clearable style="width: 120px">
          <el-option
            v-for="item in CARD_TYPES"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
          <el-option
            v-for="item in CARD_STATUSES"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="适用门店" prop="storeId">
        <el-select v-model="queryParams.storeId" filterable clearable placeholder="全部" style="width: 190px">
          <el-option
            v-for="item in storeOptions"
            :key="item.id"
            :label="`${item.name}（${item.storeNo}）`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">发卡</el-button>
      </el-col>
      <el-col :span="13">
        <el-tag type="info">发卡后为「未开卡」：不可预约、可删除；开卡时才写入有效期。状态由服务端推导（已过期／已用完不落库）</el-tag>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="cardList">
      <el-table-column label="卡号" prop="cardNo" width="172" />
      <el-table-column label="会员" prop="memberName" min-width="108" />
      <el-table-column label="开卡门店" min-width="124" show-overflow-tooltip>
        <template #default="scope">{{ storeText(scope.row.openStoreName, scope.row.openStoreNo) }}</template>
      </el-table-column>
      <el-table-column label="卡片名称" prop="cardName" min-width="115" show-overflow-tooltip />
      <el-table-column label="类型" prop="cardTypeName" width="76" align="center" />
      <el-table-column label="适用课种" prop="courseScopeName" width="80" align="center" />
      <el-table-column label="适用门店" min-width="138" show-overflow-tooltip>
        <template #default="scope">{{ scope.row.storeName || (scope.row.cardType === 2 ? '年卡不限门店' : '-') }}</template>
      </el-table-column>
      <el-table-column label="次数 / 剩余" width="105" align="center">
        <template #default="scope">{{ countText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="有效期" min-width="140">
        <template #default="scope">
          <template v-if="scope.row.validStartTime">
            <div>{{ dateOnly(scope.row.validStartTime) }}</div>
            <div class="cell-sub">至 {{ dateOnly(scope.row.validEndTime) }}</div>
          </template>
          <template v-else>
            <div>开卡后生效</div>
            <div class="cell-sub">开卡时写入</div>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="94" align="center">
        <template #default="scope">
          <el-tag :type="statusType(scope.row.status)">{{ scope.row.statusText }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="342" fixed="right" align="center">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">详情</el-button>
          <el-button v-if="scope.row.status === 0" link type="success" icon="CircleCheck" @click="handleActivate(scope.row)">开卡</el-button>
          <el-button v-if="scope.row.status === 0 || scope.row.status === 1" link type="warning" icon="VideoPause" @click="handleStop(scope.row)">停卡</el-button>
          <el-button link type="info" icon="Tickets" @click="handleFlows(scope.row)">流水</el-button>
          <el-button v-if="scope.row.status === 0" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
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

    <el-dialog v-model="open" title="会员发卡（创建待开卡）" width="640px" append-to-body>
      <el-alert
        title="发卡后为「未开卡」：不可预约、可删除；需在列表中「开卡」后才写入有效期并变为使用中。卡号由服务端生成：发卡日期 + 开卡门店编号 + 当日序列，前端不可提交。"
        type="info"
        :closable="false"
        class="mb20"
      />
      <el-form ref="cardRef" :model="form" :rules="rules" label-width="105px">
        <el-form-item label="所属会员" prop="memberId">
          <el-select v-model="form.memberId" filterable placeholder="请选择会员" style="width: 100%">
            <el-option
              v-for="item in memberOptions"
              :key="item.id"
              :label="`${item.name}（${item.phone}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="开卡门店" prop="openStoreId">
          <el-select v-model="form.openStoreId" filterable placeholder="请选择开卡门店（卡号里的门店编号取自它）" style="width: 100%">
            <el-option
              v-for="item in storeOptions"
              :key="item.id"
              :label="`${item.name}（${item.storeNo}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="卡片名称" prop="cardName">
          <el-input v-model="form.cardName" placeholder="如：普拉提次卡 / 私教次卡 / 瑜伽年卡" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="卡片类型" prop="cardType">
          <el-radio-group v-model="form.cardType" @change="handleCardTypeChange">
            <el-radio :value="1">次数卡</el-radio>
            <el-radio :value="2">年卡</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="适用课种" prop="courseScope">
          <el-select v-model="form.courseScope" style="width: 100%">
            <el-option
              v-for="item in COURSE_SCOPES"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <template v-if="form.cardType === 1">
          <el-form-item label="适用门店" prop="storeId">
            <el-select v-model="form.storeId" filterable placeholder="次数卡必填（与开卡门店可不同）" style="width: 100%">
              <el-option
                v-for="item in storeOptions"
                :key="item.id"
                :label="`${item.name}（${item.storeNo}）`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="购买次数" prop="purchaseTimes">
            <el-input-number v-model="form.purchaseTimes" :min="0" :max="9999" />
          </el-form-item>
          <el-form-item label="赠送次数" prop="giftTimes">
            <el-input-number v-model="form.giftTimes" :min="0" :max="9999" />
          </el-form-item>
          <el-form-item label="有效期" prop="validPeriodCount">
            <el-select v-model="form.validPeriodCount" style="width: 100%">
              <el-option label="3 个月（90 天）" :value="3" />
              <el-option label="6 个月（180 天）" :value="6" />
              <el-option label="12 个月（360 天）" :value="12" />
            </el-select>
          </el-form-item>
          <div class="form-tip">
            剩余次数 = 购买 ＋ 赠送 = {{ (form.purchaseTimes || 0) + (form.giftTimes || 0) }} 次；有效期按月 = 30 天计算，开卡时按服务端自然日起算（含首尾日）。
          </div>
        </template>
        <template v-else>
          <el-form-item label="适用门店">
            <el-input :model-value="'年卡不绑定门店'" readonly />
          </el-form-item>
          <el-form-item label="有效期">
            <el-input :model-value="'1 年（365 天）'" readonly />
          </el-form-item>
          <div class="form-tip">年卡不按次数计：开卡后 1 年内不限次，预约只校验有效期与适用范围，不写扣次流水。</div>
        </template>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确认发卡</el-button>
          <el-button @click="open = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="openStop" title="会员卡停卡" width="520px" append-to-body>
      <el-alert
        title="停卡后保留剩余次数与有效期，有效期不顺延；已过期、已用完的会员卡不能停卡；二期不提供恢复停卡。"
        type="warning"
        :closable="false"
        class="mb20"
      />
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
        <el-button type="danger" @click="submitStop">确认停卡</el-button>
        <el-button @click="openStop = false">返 回</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="openFlow" :title="`扣次／返次流水 · ${flowCard.cardName}`" width="780px" append-to-body>
      <el-alert
        v-if="flowCard.cardType === 2"
        title="年卡不扣次，不会产生流水。"
        type="info"
        :closable="false"
        class="mb20"
      />
      <el-table v-loading="flowLoading" :data="flowList">
        <el-table-column label="流水ID" prop="id" width="100" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.flowType === 1 ? 'warning' : 'success'">{{ scope.row.flowTypeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变动次数" prop="changeTimes" width="95" align="center" />
        <el-table-column label="变动前剩余" prop="beforeTimes" width="105" align="center" />
        <el-table-column label="变动后剩余" prop="afterTimes" width="105" align="center" />
        <el-table-column label="关联预约" prop="bookingId" width="110" />
        <el-table-column label="发生时间" prop="createTime" width="180" />
      </el-table>
      <pagination
        v-show="flowTotal > 0"
        :total="flowTotal"
        v-model:page="flowQuery.pageNum"
        v-model:limit="flowQuery.pageSize"
        @pagination="loadFlows"
      />
      <template #footer>
        <el-button @click="openFlow = false">关 闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="openView" title="会员卡详情" width="720px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="卡号">{{ detail.cardNo }}</el-descriptions-item>
        <el-descriptions-item label="卡片状态">
          <el-tag :type="statusType(detail.status)">{{ detail.statusText }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="所属会员">{{ detail.memberName }}</el-descriptions-item>
        <el-descriptions-item label="卡片名称">{{ detail.cardName }}</el-descriptions-item>
        <el-descriptions-item label="卡片类型">{{ detail.cardTypeName }}</el-descriptions-item>
        <el-descriptions-item label="适用课种">{{ detail.courseScopeName }}</el-descriptions-item>
        <el-descriptions-item label="开卡门店">{{ storeText(detail.openStoreName, detail.openStoreNo) }}</el-descriptions-item>
        <el-descriptions-item label="适用门店">{{ detail.storeName || (detail.cardType === 2 ? '年卡不限门店' : '-') }}</el-descriptions-item>
        <el-descriptions-item label="购买次数">{{ valueOrDash(detail.purchaseTimes) }}</el-descriptions-item>
        <el-descriptions-item label="赠送次数">{{ valueOrDash(detail.giftTimes) }}</el-descriptions-item>
        <el-descriptions-item label="剩余次数">{{ valueOrDash(detail.remainingTimes) }}</el-descriptions-item>
        <el-descriptions-item label="总次数（购买＋赠送）">{{ valueOrDash(detail.totalTimes) }}</el-descriptions-item>
        <el-descriptions-item label="有效期">{{ validityText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="剩余天数">{{ detail.remainingDays === null || detail.remainingDays === undefined ? '-' : `${detail.remainingDays} 天` }}</el-descriptions-item>
        <el-descriptions-item label="开卡时间">{{ detail.activateTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发卡时间">{{ detail.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="停卡时间">{{ detail.stopTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="停卡原因">{{ detail.stopReason || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="openView = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="MemberCard">
import {
  activateMemberCard,
  addMemberCard,
  delMemberCard,
  getMemberCard,
  listMemberCard,
  listMemberCardFlow,
  stopMemberCard
} from '@/api/member-card/memberCard'
import { listMember } from '@/api/member/member'
import { listStore } from '@/api/store/store'

const { proxy } = getCurrentInstance()

// 码值只用于下拉选项的取值；列表展示一律用服务端返回的 cardTypeName / courseScopeName / statusText
const CARD_TYPES = [
  { value: 1, label: '次数卡' },
  { value: 2, label: '年卡' }
]
const COURSE_SCOPES = [
  { value: 1, label: '团课类' },
  { value: 2, label: '私教课' }
]
const CARD_STATUSES = [
  { value: 0, label: '未开卡' },
  { value: 1, label: '使用中' },
  { value: 2, label: '已停卡' },
  { value: 3, label: '已过期' },
  { value: 4, label: '已用完' }
]

const cardList = ref([])
const memberOptions = ref([])
const storeOptions = ref([])
const loading = ref(true)
const showSearch = ref(true)
const open = ref(false)
const openStop = ref(false)
const openFlow = ref(false)
const openView = ref(false)
const total = ref(0)
const detail = ref({})
const currentRow = ref({})
const flowCard = ref({})
const flowList = ref([])
const flowLoading = ref(false)
const flowTotal = ref(0)

const emptyForm = () => ({
  memberId: undefined,
  openStoreId: undefined,
  cardName: '',
  cardType: 1,
  courseScope: 1,
  storeId: undefined,
  purchaseTimes: 10,
  giftTimes: 0,
  validPeriodCount: 12
})

const data = reactive({
  form: emptyForm(),
  stopForm: { stopReason: '' },
  flowQuery: { pageNum: 1, pageSize: 10 },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    memberId: undefined,
    cardNo: undefined,
    cardType: undefined,
    status: undefined,
    storeId: undefined
  },
  rules: {
    memberId: [{ required: true, message: '请选择所属会员', trigger: 'change' }],
    openStoreId: [{ required: true, message: '请选择开卡门店', trigger: 'change' }],
    cardName: [{ required: true, message: '请输入卡片名称', trigger: 'blur' }],
    cardType: [{ required: true, message: '请选择卡片类型', trigger: 'change' }],
    courseScope: [{ required: true, message: '请选择适用课种', trigger: 'change' }],
    storeId: [{ required: true, message: '次数卡必须指定适用门店', trigger: 'change' }],
    purchaseTimes: [{ required: true, message: '请输入购买次数', trigger: 'change' }],
    giftTimes: [{ required: true, message: '请输入赠送次数', trigger: 'change' }],
    validPeriodCount: [{ required: true, message: '请选择有效期', trigger: 'change' }]
  },
  stopRules: {
    stopReason: [{ required: true, message: '请输入停卡原因', trigger: 'blur' }]
  }
})

const { queryParams, form, stopForm, stopRules, flowQuery } = toRefs(data)

function storeText(name, storeNo) {
  if (!name) {
    return '-'
  }
  return storeNo ? `${name}（${storeNo}）` : name
}

function valueOrDash(value) {
  return value === null || value === undefined || value === '' ? '-' : value
}

function dateOnly(value) {
  return value ? String(value).slice(0, 10) : ''
}

function countText(row) {
  if (Number(row.cardType) === 1) {
    return `${valueOrDash(row.remainingTimes)} / ${valueOrDash(row.totalTimes)}`
  }
  return row.remainingDays === null || row.remainingDays === undefined ? '-' : `剩余 ${row.remainingDays} 天`
}

function validityText(row) {
  if (!row.validStartTime || !row.validEndTime) {
    return '开卡后生效'
  }
  return `${dateOnly(row.validStartTime)} 至 ${dateOnly(row.validEndTime)}`
}
function statusType(status) {
  return ['info', 'success', 'danger', 'warning', 'primary'][Number(status)] || 'info'
}

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

function loadOptions() {
  // 会员已无状态位，不再按 status 过滤
  Promise.all([
    listMember({ pageNum: 1, pageSize: 100 }),
    listStore({ pageNum: 1, pageSize: 100, status: 1 })
  ]).then(([members, stores]) => {
    memberOptions.value = members.rows || []
    storeOptions.value = stores.rows || []
  })
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function handleAdd() {
  form.value = emptyForm()
  open.value = true
  nextTick(() => proxy.$refs.cardRef?.clearValidate())
}

function handleCardTypeChange(cardType) {
  if (Number(cardType) === 2) {
    // 年卡：不绑门店、不按次数计，服务端固定 1 年
    form.value.storeId = undefined
    form.value.purchaseTimes = 0
    form.value.giftTimes = 0
    form.value.validPeriodCount = undefined
  } else {
    form.value.purchaseTimes = 10
    form.value.giftTimes = 0
    form.value.validPeriodCount = 12
  }
  nextTick(() => proxy.$refs.cardRef?.clearValidate())
}

function submitForm() {
  proxy.$refs.cardRef.validate(valid => {
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
    if (Number(form.value.cardType) === 1) {
      payload.storeId = form.value.storeId
      payload.purchaseTimes = form.value.purchaseTimes
      payload.giftTimes = form.value.giftTimes
      payload.validPeriodCount = form.value.validPeriodCount
    }
    addMemberCard(payload).then(() => {
      proxy.$modal.msgSuccess('发卡成功，卡片为未开卡状态')
      open.value = false
      getList()
    }).catch(error => {
      proxy.$modal.msgError(error?.message || '发卡失败')
    })
  })
}

function handleActivate(row) {
  proxy.$modal.confirm(`确认对“${row.cardName}”正式开卡吗？开卡后有效期按今天起算，不可撤销。`).then(() => {
    return activateMemberCard(row.id)
  }).then(response => {
    const endTime = response?.data?.validEndTime
    proxy.$modal.msgSuccess(endTime ? `开卡成功，有效期至 ${dateOnly(endTime)}` : '开卡成功')
    getList()
  }).catch(error => {
    if (error !== 'cancel') {
      proxy.$modal.msgError(error?.message || '开卡失败')
    }
  })
}

function handleStop(row) {
  currentRow.value = row
  stopForm.value.stopReason = ''
  openStop.value = true
  nextTick(() => proxy.$refs.stopRef?.clearValidate())
}

function submitStop() {
  proxy.$refs.stopRef.validate(valid => {
    if (!valid) {
      return
    }
    stopMemberCard(currentRow.value.id, { stopReason: stopForm.value.stopReason }).then(() => {
      proxy.$modal.msgSuccess('停卡成功，剩余次数与有效期保留')
      openStop.value = false
      getList()
    }).catch(error => {
      proxy.$modal.msgError(error?.message || '停卡失败')
    })
  })
}

function handleDelete(row) {
  proxy.$modal.confirm(`确认删除未开卡的“${row.cardName}（${row.cardNo}）”吗？`).then(() => {
    return delMemberCard(row.id)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
  }).catch(error => {
    // 已开卡的卡不可删：原样展示服务端提示
    if (error !== 'cancel') {
      proxy.$modal.msgError(error?.message || '删除失败')
    }
  })
}

function handleFlows(row) {
  flowCard.value = row
  flowQuery.value.pageNum = 1
  openFlow.value = true
  loadFlows()
}

function loadFlows() {
  flowLoading.value = true
  listMemberCardFlow(flowCard.value.id, flowQuery.value).then(response => {
    flowList.value = response.rows || []
    flowTotal.value = response.total || 0
    flowLoading.value = false
  }).catch(() => {
    flowLoading.value = false
  })
}

function handleView(row) {
  getMemberCard(row.id).then(response => {
    detail.value = response.data || row
    openView.value = true
  }).catch(error => {
    proxy.$modal.msgError(error?.message || '加载详情失败')
  })
}

loadOptions()
getList()
</script>

<style scoped>
.form-tip {
  margin: 0 0 12px 105px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}

/* 有效期第二行：与第一行同宽避免表格行高跳动 */
.cell-sub {
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}
</style>
