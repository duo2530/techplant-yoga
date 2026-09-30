<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch">
      <el-form-item label="用户ID" prop="userId"><el-input v-model="queryParams.userId" clearable placeholder="请输入用户ID" style="width: 180px" /></el-form-item>
      <el-form-item label="排班ID" prop="scheduleId"><el-input v-model="queryParams.scheduleId" clearable placeholder="请输入排班ID" style="width: 180px" /></el-form-item>
      <el-form-item label="状态" prop="bookingStatus"><el-select v-model="queryParams.bookingStatus" clearable placeholder="请选择状态" style="width: 140px"><el-option label="已预约" :value="1" /><el-option label="已取消" :value="2" /><el-option label="已签到" :value="3" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-row :gutter="10" class="mb8"><right-toolbar v-model:showSearch="showSearch" @queryTable="getList" /></el-row>
    <el-table v-loading="loading" :data="bookingList">
      <el-table-column label="预约ID" prop="id" min-width="170" />
      <el-table-column label="用户ID" prop="userId" min-width="170" />
      <el-table-column label="排班ID" prop="scheduleId" min-width="170" />
      <el-table-column label="门店ID" prop="storeId" min-width="170" />
      <el-table-column label="课程ID" prop="courseId" min-width="170" />
      <el-table-column label="预约人数" prop="bookingCount" width="90" align="center" />
      <el-table-column label="状态" width="90" align="center"><template #default="scope"><el-tag>{{ statusLabel(scope.row.bookingStatus) }}</el-tag></template></el-table-column>
      <el-table-column label="预约时间" prop="createTime" width="170" />
      <el-table-column label="操作" width="80" fixed="right"><template #default="scope"><el-button link type="primary" @click="handleView(scope.row)">详情</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    <el-dialog v-model="open" title="预约详情" width="520px" append-to-body><el-descriptions :column="1" border><el-descriptions-item label="预约ID">{{ detail.id }}</el-descriptions-item><el-descriptions-item label="用户ID">{{ detail.userId }}</el-descriptions-item><el-descriptions-item label="排班ID">{{ detail.scheduleId }}</el-descriptions-item><el-descriptions-item label="门店ID">{{ detail.storeId }}</el-descriptions-item><el-descriptions-item label="课程ID">{{ detail.courseId }}</el-descriptions-item><el-descriptions-item label="预约状态">{{ statusLabel(detail.bookingStatus) }}</el-descriptions-item><el-descriptions-item label="预约人数">{{ detail.bookingCount }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ detail.createTime }}</el-descriptions-item></el-descriptions></el-dialog>
  </div>
</template>

<script setup name="Booking">
import { listBooking, getBooking } from '@/api/booking/booking'
const { proxy } = getCurrentInstance()
const bookingList = ref([]); const detail = ref({}); const loading = ref(true); const showSearch = ref(true); const total = ref(0); const open = ref(false)
const data = reactive({ queryParams: { pageNum: 1, pageSize: 10, userId: undefined, scheduleId: undefined, bookingStatus: undefined } })
const { queryParams } = toRefs(data)
function statusLabel(value) { return value === 1 ? '已预约' : value === 2 ? '已取消' : '已签到' }
function getList() { loading.value = true; listBooking(queryParams.value).then(r => { bookingList.value = r.rows || []; total.value = r.total || 0 }).finally(() => { loading.value = false }) }
function handleQuery() { queryParams.value.pageNum = 1; getList() }
function resetQuery() { proxy.resetForm('queryRef'); handleQuery() }
function handleView(row) { getBooking(row.id).then(r => { detail.value = r.data; open.value = true }) }
getList()
</script>
