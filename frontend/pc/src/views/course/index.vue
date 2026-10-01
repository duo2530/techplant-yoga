<template>
  <div class="app-container">
    <!-- 查询条件：课程名称 / 课种（课程没有门店与状态） -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="课程名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入课程名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="课种" prop="courseType">
        <el-select v-model="queryParams.courseType" placeholder="请选择课种" clearable style="width: 200px">
          <el-option v-for="item in COURSE_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <!-- 一期不做 RBAC（BR-全局-003）：不挂 v-hasPermi，业务接口只要求登录 -->
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增课程</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="courseList">
      <el-table-column label="封面" align="center" prop="coverUrl" width="90">
        <template #default="scope">
          <el-image
            v-if="scope.row.coverUrl"
            :src="scope.row.coverUrl"
            :preview-src-list="[scope.row.coverUrl]"
            preview-teleported
            fit="cover"
            style="width: 40px; height: 40px"
          />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="课程名称" prop="name" :show-overflow-tooltip="true" min-width="160" />
      <!-- 课种名称用后端成对返回的 courseTypeName（不前端硬编码码→名） -->
      <el-table-column label="课种" align="center" prop="courseTypeName" width="110">
        <template #default="scope">{{ scope.row.courseTypeName || '-' }}</template>
      </el-table-column>
      <el-table-column label="课程难度" align="center" prop="difficulty" width="110">
        <template #default="scope">{{ difficultyLabel(scope.row.difficulty) }}</template>
      </el-table-column>
      <el-table-column label="更新时间" align="center" prop="updateTime" width="170" />
      <el-table-column label="操作" align="center" width="220" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">编辑</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
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

    <!-- 新增 / 编辑课程：课程无状态，表单里没有状态开关、没有门店 -->
    <el-dialog :title="title" v-model="open" width="680px" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb8"
        title="改课种不影响既有排课（已快照），改难度会影响既有排课展示"
      />
      <el-form ref="courseRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="课程名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入课程名称" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="课种" prop="courseType">
          <el-select v-model="form.courseType" placeholder="请选择课种" style="width: 100%">
            <el-option v-for="item in COURSE_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程难度" prop="difficulty">
          <el-select v-model="form.difficulty" placeholder="请选择课程难度" style="width: 100%">
            <el-option v-for="item in difficultyOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程封面" prop="coverUrl">
          <image-upload v-model="form.coverUrl" :limit="1" />
        </el-form-item>
        <el-form-item label="课程介绍" prop="intro">
          <el-input v-model="form.intro" type="textarea" :rows="4" placeholder="请输入课程介绍" maxlength="1024" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 查看课程详情：课种名称同样取后端返回的 courseTypeName -->
    <el-dialog title="课程详情" v-model="openView" width="680px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="课程名称">{{ viewForm.name }}</el-descriptions-item>
        <el-descriptions-item label="课种">{{ viewForm.courseTypeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="课程难度">{{ difficultyLabel(viewForm.difficulty) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ viewForm.updateTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="课程封面" :span="2">
          <el-image
            v-if="viewForm.coverUrl"
            :src="viewForm.coverUrl"
            :preview-src-list="[viewForm.coverUrl]"
            preview-teleported
            fit="cover"
            style="width: 120px; height: 120px"
          />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="课程介绍" :span="2">
          <span style="white-space: pre-wrap">{{ viewForm.intro || '-' }}</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="openView = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Course">
import { listCourse, getCourse, addCourse, updateCourse, delCourse } from "@/api/course/course"

const { proxy } = getCurrentInstance()

/**
 * 课种下拉选项：只用于「表单控件」本身。
 * 列表与详情的课种名称一律用后端成对返回的 courseTypeName，前端不承担码→名翻译
 * （详细设计总览 §2「枚举返回」）。
 */
const COURSE_TYPE_OPTIONS = [
  { value: 1, label: "团课" },
  { value: 2, label: "精品课" },
  { value: 3, label: "私教课" },
  { value: 4, label: "特色课" }
]

/** 难度下拉 1~5 星 */
const difficultyOptions = [
  { value: 1, label: "1 星" },
  { value: 2, label: "2 星" },
  { value: 3, label: "3 星" },
  { value: 4, label: "4 星" },
  { value: 5, label: "5 星" }
]

const courseList = ref([])
const open = ref(false)
const openView = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const title = ref("")

const data = reactive({
  form: {},
  viewForm: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: undefined,
    courseType: undefined
  },
  rules: {
    name: [
      { required: true, message: "课程名称不能为空", trigger: "blur" },
      { max: 64, message: "课程名称长度不能超过 64", trigger: "blur" }
    ],
    courseType: [{ required: true, message: "课种不能为空", trigger: "change" }],
    difficulty: [{ required: true, type: "number", message: "请选择课程难度（1~5 星）", trigger: "change" }]
  }
})

const { form, viewForm, queryParams, rules } = toRefs(data)

/** 难度取标签（纯展示，不涉及课种码→名） */
function difficultyLabel(difficulty) {
  const hit = difficultyOptions.find(item => item.value === difficulty)
  return hit ? hit.label : "-"
}

/** 查询课程列表 */
function getList() {
  loading.value = true
  listCourse(queryParams.value).then(response => {
    courseList.value = response.rows
    total.value = response.total
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
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 表单重置 */
function reset() {
  form.value = {
    id: undefined,
    name: undefined,
    courseType: undefined,
    difficulty: undefined,
    coverUrl: undefined,
    intro: undefined
  }
  proxy.resetForm("courseRef")
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增课程"
}

/** 编辑按钮操作：详情回显（课程无状态，不放进表单） */
function handleUpdate(row) {
  reset()
  getCourse(row.id).then(response => {
    const detail = response.data
    form.value = {
      id: detail.id,
      name: detail.name,
      courseType: detail.courseType,
      difficulty: detail.difficulty,
      coverUrl: detail.coverUrl,
      intro: detail.intro
    }
    open.value = true
    title.value = "修改课程"
  })
}

/** 查看按钮操作 */
function handleView(row) {
  getCourse(row.id).then(response => {
    viewForm.value = response.data
    openView.value = true
  })
}

/** 提交按钮：只提交设计约定的字段（无门店、无状态） */
function submitForm() {
  proxy.$refs["courseRef"].validate(valid => {
    if (!valid) {
      return
    }
    const payload = {
      name: form.value.name,
      courseType: form.value.courseType,
      difficulty: form.value.difficulty,
      coverUrl: form.value.coverUrl || null,
      intro: form.value.intro || null
    }
    const request = form.value.id ? updateCourse(form.value.id, payload) : addCourse(payload)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.id ? "修改成功" : "新增成功")
      open.value = false
      getList()
    }).catch(() => {})
  })
}

/** 删除按钮操作：被未结束排课引用时后端返回 409，提示语由 axios 拦截器统一弹出 */
function handleDelete(row) {
  proxy.$modal.confirm('确认要删除课程"' + row.name + '"吗？').then(() => {
    return delCourse(row.id)
  }).then(() => {
    proxy.$modal.msgSuccess("删除成功")
    getList()
  }).catch(() => {})
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

getList()
</script>
