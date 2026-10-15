<template>
  <div class="app-container">
    <!-- 查询条件：会员 / 状态 / 卡类型 / 门店 / 课程 / 上课日期范围 / 排课 ID -->
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch">
      <el-form-item label="会员" prop="memberId">
        <el-select v-model="queryParams.memberId" filterable clearable placeholder="全部" style="width: 210px">
          <el-option v-for="item in memberOptions" :key="item.id" :label="memberLabel(item)" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="item in STATUSES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="卡类型" prop="cardType">
        <el-select v-model="queryParams.cardType" clearable placeholder="全部" style="width: 130px">
          <el-option v-for="item in CARD_TYPES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="门店" prop="storeId">
        <el-select v-model="queryParams.storeId" filterable clearable placeholder="全部" style="width: 190px">
          <el-option v-for="item in storeOptions" :key="item.id" :label="storeLabel(item)" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="课程" prop="courseId">
        <el-select v-model="queryParams.courseId" filterable clearable placeholder="全部" style="width: 190px">
          <el-option v-for="item in courseOptions" :key="item.id" :label="courseLabel(item)" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="上课日期">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 260px"
        />
      </el-form-item>
      <el-form-item label="排课 ID" prop="scheduleId">
        <el-input
          v-model="queryParams.scheduleId"
          clearable
          placeholder="排课 ID"
          style="width: 190px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="reservationList">
      <el-table-column label="预约ID" prop="id" min-width="170" show-overflow-tooltip />
      <el-table-column label="会员" min-width="140">
        <template #default="scope">
          <span>{{ scope.row.memberName || '-' }}</span>
          <div v-if="scope.row.memberPhone" class="sub-text">{{ scope.row.memberPhone }}</div>
        </template>
      </el-table-column>
      <el-table-column label="课程" min-width="150">
        <template #default="scope">
          <span>{{ scope.row.courseName || '-' }}</span>
          <div v-if="scope.row.courseTypeName" class="sub-text">{{ scope.row.courseTypeName }}</div>
        </template>
      </el-table-column>
      <el-table-column label="教练" min-width="100">
        <template #default="scope">{{ scope.row.coachName || '-' }}</template>
      </el-table-column>
      <el-table-column label="门店/教室" min-width="150">
        <template #default="scope">
          <span>{{ scope.row.storeName || '-' }}</span>
          <div v-if="scope.row.classroomName" class="sub-text">{{ scope.row.classroomName }}</div>
        </template>
      </el-table-column>
      <el-table-column label="上课时间" min-width="190">
        <template #default="scope">{{ classTimeText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="时长" width="90" align="center">
        <template #default="scope">{{ scope.row.durationMinutes ? scope.row.durationMinutes + ' 分钟' : '-' }}</template>
      </el-table-column>
      <el-table-column label="人数" prop="persons" width="70" align="center" />
      <el-table-column label="会员卡" min-width="190">
        <template #default="scope">{{ cardText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="扣次" prop="deductTimes" width="70" align="center" />
      <el-table-column label="预约状态" width="95" align="center">
        <template #default="scope">
          <el-tag :type="reservationTagType(scope.row.statusText)">{{ scope.row.statusText || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排课状态" width="95" align="center">
        <template #default="scope">
          <el-tag :type="scheduleTagType(scope.row.scheduleStatusText)">{{ scope.row.scheduleStatusText || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="预约时间" min-width="165">
        <template #default="scope">{{ scope.row.reservationTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="取消时间/原因" min-width="200">
        <template #default="scope">
          <span>{{ scope.row.cancelTime || '-' }}</span>
          <div v-if="scope.row.cancelTime" class="sub-text">{{ cancelReasonText(scope.row.cancelReason) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="签到时间" min-width="165">
        <template #default="scope">{{ scope.row.checkInTime || '-' }}</template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </div>
</template>

<script setup name="Reservation">
import { listReservation } from '@/api/reservation/reservation'
import { listMember } from '@/api/member/member'
import { listStore } from '@/api/store/store'
import { listCourse } from '@/api/course/course'

const { proxy } = getCurrentInstance()

// 预约状态与卡类型是后端固定枚举（不建字典），值域与 statusText / cardTypeText 一致
const STATUSES = [
  { value: 1, label: '已预约' },
  { value: 2, label: '已取消' },
  { value: 3, label: '已签到' }
]
const CARD_TYPES = [
  { value: 1, label: '次数卡' },
  { value: 2, label: '年卡' }
]

const reservationList = ref([])
const memberOptions = ref([])
const storeOptions = ref([])
const courseOptions = ref([])
const dateRange = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)

const data = reactive({
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    storeId: undefined,
    courseId: undefined,
    scheduleId: undefined,
    memberId: undefined,
    status: undefined,
    cardType: undefined
  }
})

const { queryParams } = toRefs(data)

function memberLabel(member) {
  return member.phone ? `${member.name}（${member.phone}）` : member.name
}

function storeLabel(store) {
  return store.name
}

function courseLabel(course) {
  return course.name
}

function timeText(value) {
  return value ? proxy.parseTime(value, '{h}:{i}') : ''
}

/** 上课日期取 scheduleDate，起止时间只取时分 */
function classTimeText(row) {
  const clock = [timeText(row.startTime), timeText(row.endTime)].filter(Boolean).join('-')
  return [row.scheduleDate, clock].filter(Boolean).join(' ') || '-'
}

function cardText(row) {
  return [row.cardNo, row.cardName, row.cardTypeText].filter(Boolean).join(' / ') || '-'
}

/** 取消原因码只在有取消时间时才有展示意义 */
function cancelReasonText(reason) {
  if (reason === 1) {
    return '会员主动取消'
  }
  return reason === 2 ? '排课取消' : '-'
}

/** 预约状态标签颜色，文案由后端 statusText 给出 */
function reservationTagType(statusText) {
  if (statusText === '已预约') return 'primary'
  if (statusText === '已签到') return 'success'
  return 'info'
}

/** 已结束、已取消用 info，其余排课状态用 success */
function scheduleTagType(scheduleStatusText) {
  return scheduleStatusText === '已结束' || scheduleStatusText === '已取消' ? 'info' : 'success'
}

/** 下拉数据：会员、门店、课程（分页上限 100 条，雪花 ID 直接作为 :value 字符串） */
function getOptions() {
  listMember({ pageNum: 1, pageSize: 100 }).then(response => {
    memberOptions.value = response.rows || []
  })
  listStore({ pageNum: 1, pageSize: 100 }).then(response => {
    storeOptions.value = response.rows || []
  })
  listCourse({ pageNum: 1, pageSize: 100 }).then(response => {
    courseOptions.value = response.rows || []
  })
}

/** 查询预约列表；上课日期按接口参数约定拼 startDate／endDate（含当天） */
function getList() {
  loading.value = true
  const params = { ...queryParams.value }
  if (dateRange.value && dateRange.value.length === 2) {
    params.startDate = dateRange.value[0]
    params.endDate = dateRange.value[1]
  }
  listReservation(params).then(response => {
    reservationList.value = response.rows || []
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
  dateRange.value = []
  proxy.resetForm('queryRef')
  handleQuery()
}

getOptions()
getList()
</script>

<style scoped>
.sub-text {
  color: #909399;
  font-size: 12px;
}
</style>
