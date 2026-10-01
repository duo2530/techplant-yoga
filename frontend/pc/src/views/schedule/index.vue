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
      <el-form-item label="教练" prop="coachId">
        <el-select v-model="queryParams.coachId" clearable filterable placeholder="请选择教练" style="width: 160px">
          <el-option v-for="item in coachOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable placeholder="请选择状态" style="width: 140px">
          <el-option label="待上架" :value="1" />
          <el-option label="已上架" :value="2" />
          <el-option label="已取消" :value="3" />
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
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增排课</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="scheduleList">
      <el-table-column label="门店" prop="storeName" min-width="120" show-overflow-tooltip />
      <el-table-column label="课程" prop="courseName" min-width="140" show-overflow-tooltip />
      <el-table-column label="课种" prop="courseTypeName" width="90" align="center" />
      <el-table-column label="教练" prop="coachName" width="100" />
      <el-table-column label="教室" prop="classroomName" min-width="140" show-overflow-tooltip />
      <el-table-column label="上课日期" prop="scheduleDate" width="120" align="center" />
      <el-table-column label="开始时间" prop="startTime" width="165" />
      <el-table-column label="结束时间" prop="endTime" width="165" />
      <el-table-column label="最大人数" prop="maxPersons" width="90" align="center" />
      <el-table-column label="最低开课人数" prop="minPersons" width="110" align="center" />
      <el-table-column label="已预约" prop="bookedPersons" width="80" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)">{{ scope.row.statusText }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <!-- 按钮可用性完全按后端返回的 status 与 finished 控制，前端不自行推导「已结束」 -->
          <el-button v-if="scope.row.status === 1" link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button v-if="scope.row.status === 1" link type="success" @click="handleStatus(scope.row, 2)">上架</el-button>
          <el-button v-if="scope.row.status === 2 && !scope.row.finished" link type="warning" @click="handleStatus(scope.row, 1)">下架</el-button>
          <el-button v-if="scope.row.status === 2 && !scope.row.finished" link type="danger" @click="handleStatus(scope.row, 3)">取消</el-button>
          <el-button v-if="scope.row.status === 1" link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
          <span v-if="scope.row.status === 3 || (scope.row.status === 2 && scope.row.finished)">已终态</span>
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

    <el-dialog v-model="open" :title="title" width="620px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="门店" prop="storeId">
          <!-- 先选门店，教室下拉再按所选门店加载 -->
          <el-select v-model="form.storeId" filterable placeholder="请先选择门店" style="width: 100%" @change="handleFormStoreChange">
            <el-option v-for="item in storeOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教室" prop="classroomId">
          <el-select v-model="form.classroomId" filterable placeholder="请选择教室" style="width: 100%" :disabled="!form.storeId">
            <el-option v-for="item in formClassroomOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程" prop="courseId">
          <!-- 课程是平台级，不限门店 -->
          <el-select v-model="form.courseId" filterable placeholder="请选择课程" style="width: 100%">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教练" prop="coachId">
          <!-- 教练是平台级，不限门店 -->
          <el-select v-model="form.coachId" filterable placeholder="请选择教练" style="width: 100%">
            <el-option v-for="item in coachOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择开始时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择结束时间（须与开始时间同一天）" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最大人数" prop="maxPersons">
          <el-input-number v-model="form.maxPersons" :min="1" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item label="最低开课人数" prop="minPersons">
          <el-input-number v-model="form.minPersons" :min="1" :max="form.maxPersons || 1" controls-position="right" />
        </el-form-item>
        <el-form-item label="已预约人数">
          <!-- 一期固定 0，只读；不参与提交 -->
          <el-input-number v-model="form.bookedPersons" :min="0" disabled controls-position="right" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Schedule">
import { listStore } from '@/api/store/store'
import { listCourse } from '@/api/course/course'
import { listCoach } from '@/api/coach/coach'
import { listClassroom } from '@/api/classroom/classroom'
import { listSchedule, getSchedule, addSchedule, updateSchedule, changeScheduleStatus, delSchedule } from '@/api/schedule/schedule'

const { proxy } = getCurrentInstance()

const scheduleList = ref([])
const storeOptions = ref([])
const courseOptions = ref([])
const coachOptions = ref([])
const formClassroomOptions = ref([])
const dateRange = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const open = ref(false)
const title = ref('')

function emptyForm() {
  return {
    id: undefined,
    storeId: undefined,
    classroomId: undefined,
    courseId: undefined,
    coachId: undefined,
    startTime: undefined,
    endTime: undefined,
    maxPersons: 20,
    minPersons: 2,
    // 只读展示用，不参与提交（后端 DTO 里没有这两个字段）
    bookedPersons: 0
  }
}

const data = reactive({
  form: emptyForm(),
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    storeId: undefined,
    courseId: undefined,
    coachId: undefined,
    status: undefined,
    startDate: undefined,
    endDate: undefined
  },
  rules: {
    storeId: [{ required: true, message: '请选择门店', trigger: 'change' }],
    classroomId: [{ required: true, message: '请选择教室', trigger: 'change' }],
    courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
    coachId: [{ required: true, message: '请选择教练', trigger: 'change' }],
    startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
    endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
    maxPersons: [{ required: true, message: '请输入最大人数', trigger: 'blur' }],
    minPersons: [{ required: true, message: '请输入最低开课人数', trigger: 'blur' }]
  }
})

const { form, queryParams, rules } = data

/** 状态标签颜色（文案用后端 statusText，前端只做颜色映射） */
function statusTagType(status) {
  if (status === 1) return 'info'
  if (status === 2) return 'success'
  if (status === 3) return 'danger'
  return 'info'
}

/** 下拉选项：门店 / 课程 / 教练（后两者都是平台级，不限门店） */
function getOptions() {
  listStore({ pageNum: 1, pageSize: 100 }).then(response => {
    storeOptions.value = response.rows || []
  })
  listCourse({ pageNum: 1, pageSize: 100 }).then(response => {
    courseOptions.value = response.rows || []
  })
  listCoach({ pageNum: 1, pageSize: 100 }).then(response => {
    coachOptions.value = response.rows || []
  })
}

/** 教室下拉按所选门店加载 */
function loadClassrooms(storeId) {
  if (!storeId) {
    formClassroomOptions.value = []
    return Promise.resolve([])
  }
  return listClassroom({ storeId: storeId, pageNum: 1, pageSize: 100 }).then(response => response.rows || [])
}

/** 查询排课列表 */
function getList() {
  loading.value = true
  listSchedule(queryParams.value).then(response => {
    scheduleList.value = response.rows || []
    total.value = response.total || 0
  }).finally(() => {
    loading.value = false
  })
}

/** 搜索：日期区间写入 schedule_date 的起止条件 */
function handleQuery() {
  queryParams.value.pageNum = 1
  if (Array.isArray(dateRange.value) && dateRange.value.length === 2) {
    queryParams.value.startDate = dateRange.value[0]
    queryParams.value.endDate = dateRange.value[1]
  } else {
    queryParams.value.startDate = undefined
    queryParams.value.endDate = undefined
  }
  getList()
}

/** 重置 */
function resetQuery() {
  dateRange.value = []
  proxy.resetForm('queryRef')
  handleQuery()
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = emptyForm()
  formClassroomOptions.value = []
  proxy.resetForm('formRef')
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  title.value = '新增排课'
  open.value = true
}

/** 选择门店后清空教室并重新加载 */
function handleFormStoreChange(storeId) {
  form.value.classroomId = undefined
  loadClassrooms(storeId).then(rows => {
    formClassroomOptions.value = rows
  })
}

/** 修改按钮操作（仅「待上架」可改，按钮上已限制） */
function handleUpdate(row) {
  reset()
  const scheduleId = row.id
  getSchedule(scheduleId).then(response => {
    const detail = response.data || {}
    form.value = { ...emptyForm(), ...detail }
    return loadClassrooms(detail.storeId)
  }).then(rows => {
    formClassroomOptions.value = rows
    title.value = '修改排课'
    open.value = true
  })
}

/** 提交：只提交后端 DTO 里的 8 个字段，不带 status / bookedPersons / courseType / scheduleDate */
function submitForm() {
  proxy.$refs.formRef.validate(valid => {
    if (!valid) {
      return
    }
    const payload = {
      storeId: form.value.storeId,
      courseId: form.value.courseId,
      coachId: form.value.coachId,
      classroomId: form.value.classroomId,
      startTime: form.value.startTime,
      endTime: form.value.endTime,
      maxPersons: form.value.maxPersons,
      minPersons: form.value.minPersons
    }
    const request = form.value.id ? updateSchedule(form.value.id, payload) : addSchedule(payload)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.id ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

/** 状态流转：1 下架 / 2 上架 / 3 取消 */
function handleStatus(row, status) {
  const actionText = status === 2 ? '上架' : status === 1 ? '下架' : '取消'
  proxy.$modal.confirm('确认' + actionText + '该排课吗？').then(() => {
    return changeScheduleStatus(row.id, status)
  }).then(() => {
    proxy.$modal.msgSuccess('操作成功')
    getList()
  }).catch(() => {})
}

/** 删除按钮操作（仅「待上架」可删；被服务端拒绝时由拦截器原样提示 msg） */
function handleDelete(row) {
  proxy.$modal.confirm('是否确认删除该排课？').then(() => {
    return delSchedule(row.id)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
  }).catch(() => {})
}

getOptions()
getList()
</script>
