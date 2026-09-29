<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch">
      <el-form-item label="门店" prop="storeId">
        <el-select v-model="queryParams.storeId" clearable filterable placeholder="请选择门店" style="width: 180px">
          <el-option v-for="item in storeOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="课程" prop="courseId">
        <el-select v-model="queryParams.courseId" clearable filterable placeholder="请选择课程" style="width: 180px">
          <el-option v-for="item in courseOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable placeholder="请选择状态" style="width: 140px">
          <el-option label="可预约" :value="1" /><el-option label="已取消" :value="2" /><el-option label="已结束" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8"><el-col :span="1.5"><el-button type="primary" plain icon="Plus" @click="handleAdd">新增排班</el-button></el-col><right-toolbar v-model:showSearch="showSearch" @queryTable="getList" /></el-row>
    <el-table v-loading="loading" :data="scheduleList">
      <el-table-column label="门店" min-width="130"><template #default="scope">{{ storeLabel(scope.row.storeId) }}</template></el-table-column>
      <el-table-column label="课程" min-width="150"><template #default="scope">{{ courseLabel(scope.row.courseId) }}</template></el-table-column>
      <el-table-column label="开始时间" prop="startTime" width="170" />
      <el-table-column label="结束时间" prop="endTime" width="170" />
      <el-table-column label="容量" prop="capacity" width="80" align="center" />
      <el-table-column label="已预约" prop="bookingCount" width="90" align="center" />
      <el-table-column label="状态" width="90" align="center"><template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="210" fixed="right" align="center"><template #default="scope"><el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">编辑</el-button><el-button link :type="scope.row.status === 1 ? 'danger' : 'success'" @click="handleStatus(scope.row)">{{ scope.row.status === 1 ? '取消' : '设为可预约' }}</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="open" :title="title" width="620px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="门店" prop="storeId"><el-select v-model="form.storeId" filterable placeholder="请选择门店" style="width: 100%"><el-option v-for="item in storeOptions" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="课程" prop="courseId"><el-select v-model="form.courseId" filterable placeholder="请选择课程" style="width: 100%"><el-option v-for="item in courseOptions" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="开始时间" prop="startTime"><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择开始时间" style="width: 100%" /></el-form-item>
        <el-form-item label="结束时间" prop="endTime"><el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择结束时间" style="width: 100%" /></el-form-item>
        <el-form-item label="时间段容量" prop="capacity"><el-input-number v-model="form.capacity" :min="1" :max="9999" controls-position="right" /></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="submitForm">确 定</el-button><el-button @click="open = false">取 消</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="Schedule">
import { listStore } from '@/api/store/store'
import { listCourse } from '@/api/course/course'
import { addSchedule, listSchedule, getSchedule, updateSchedule, updateScheduleStatus } from '@/api/schedule/schedule'

const { proxy } = getCurrentInstance()
const scheduleList = ref([]); const storeOptions = ref([]); const courseOptions = ref([])
const loading = ref(true); const showSearch = ref(true); const total = ref(0); const open = ref(false); const title = ref('')
const emptyForm = () => ({ id: undefined, storeId: undefined, courseId: undefined, startTime: undefined, endTime: undefined, capacity: 20 })
const data = reactive({ form: emptyForm(), queryParams: { pageNum: 1, pageSize: 10, storeId: undefined, courseId: undefined, status: undefined }, rules: { storeId: [{ required: true, message: '请选择门店', trigger: 'change' }], courseId: [{ required: true, message: '请选择课程', trigger: 'change' }], startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }], endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }], capacity: [{ required: true, message: '请输入容量', trigger: 'blur' }] } })
const { form, queryParams, rules } = toRefs(data)
function getOptions() { listStore({ pageNum: 1, pageSize: 100, status: 1 }).then(r => { storeOptions.value = r.rows || [] }); listCourse({ pageNum: 1, pageSize: 100, status: 1 }).then(r => { courseOptions.value = r.rows || [] }) }
function getList() { loading.value = true; listSchedule(queryParams.value).then(r => { scheduleList.value = r.rows || []; total.value = r.total || 0 }).finally(() => { loading.value = false }) }
function handleQuery() { queryParams.value.pageNum = 1; getList() }
function resetQuery() { proxy.resetForm('queryRef'); handleQuery() }
function storeLabel(id) { const item = storeOptions.value.find(x => String(x.id) === String(id)); return item ? item.name : id || '-' }
function courseLabel(id) { const item = courseOptions.value.find(x => String(x.id) === String(id)); return item ? item.name : id || '-' }
function statusLabel(value) { return value === 1 ? '可预约' : value === 2 ? '已取消' : '已结束' }
function handleAdd() { form.value = emptyForm(); title.value = '新增排班'; open.value = true }
function handleUpdate(row) { getSchedule(row.id).then(r => { form.value = { ...r.data }; title.value = '修改排班'; open.value = true }) }
function submitForm() { proxy.$refs.formRef.validate(valid => { if (!valid) return; const action = form.value.id ? updateSchedule(form.value.id, form.value) : addSchedule(form.value); action.then(() => { proxy.$modal.msgSuccess(form.value.id ? '修改成功' : '新增成功'); open.value = false; getList() }) }) }
function handleStatus(row) { const target = row.status === 1 ? 2 : 1; proxy.$modal.confirm('确认修改该排班状态吗？').then(() => updateScheduleStatus(row.id, target)).then(() => { proxy.$modal.msgSuccess('操作成功'); getList() }).catch(() => {}) }
getOptions(); getList()
</script>
