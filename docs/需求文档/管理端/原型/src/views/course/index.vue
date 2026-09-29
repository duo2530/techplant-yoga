<template>
  <div class="app-container">
    <!-- 查询条件：课程名称 / 课种 / 课程状态 -->
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
      <el-form-item label="课种" prop="type">
        <el-select v-model="queryParams.type" placeholder="请选择课种" clearable style="width: 200px">
          <el-option v-for="item in COURSE_TYPES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="课程状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择课程状态" clearable style="width: 200px">
          <el-option v-for="item in COURSE_STATUSES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <!-- 权限：本次只挂菜单、不做按钮鉴权（2026-09-22 决策），
             后续启用 RBAC 时补 v-hasPermi="['course:course:add']" 并放开菜单 SQL 里的按钮权限串 -->
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增课程</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="courseList">
      <el-table-column label="课程名称" prop="name" :show-overflow-tooltip="true" min-width="160" />
      <el-table-column label="课种" align="center" prop="type" width="100">
        <template #default="scope">{{ labelOf(COURSE_TYPES, scope.row.type) }}</template>
      </el-table-column>
      <el-table-column label="课程难度" align="center" prop="difficulty" width="120">
        <template #default="scope">{{ difficultyLabel(scope.row.difficulty) }}</template>
      </el-table-column>
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
      <el-table-column label="排序" align="center" prop="sortNo" width="80" />
      <el-table-column label="状态" align="center" prop="status" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
            {{ labelOf(COURSE_STATUSES, scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" align="center" prop="updateTime" width="170" />
      <el-table-column label="操作" align="center" width="220" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">编辑</el-button>
          <el-button
            link
            :type="scope.row.status === 1 ? 'danger' : 'success'"
            @click="handleStatusChange(scope.row)"
          >{{ scope.row.status === 1 ? '停用' : '启用' }}</el-button>
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

    <!-- 新增 / 编辑课程：请求体不含 status（状态只走列表上的启用/停用），见 §2.2.3、§2.2.4 -->
    <el-dialog :title="title" v-model="open" width="680px" append-to-body>
      <el-form ref="courseRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="课程名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入课程名称" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="课种" prop="type">
          <el-select v-model="form.type" placeholder="请选择课种" style="width: 100%">
            <el-option v-for="item in COURSE_TYPES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程难度" prop="difficulty">
          <el-rate v-model="form.difficulty" :max="5" show-score score-template="{value} 星" />
        </el-form-item>
        <el-form-item label="单节时长" prop="durationMin">
          <el-input-number v-model="form.durationMin" :min="1" controls-position="right" placeholder="分钟" />
          <span class="form-tip">单位：分钟，可留空</span>
        </el-form-item>
        <el-form-item label="展示排序" prop="sortNo">
          <el-input-number v-model="form.sortNo" :min="0" controls-position="right" />
          <span class="form-tip">值越小越靠前</span>
        </el-form-item>
        <el-form-item label="课程封面" prop="coverUrl">
          <image-upload v-model="form.coverUrl" :limit="1" />
        </el-form-item>
        <el-form-item label="课程介绍" prop="intro">
          <el-input v-model="form.intro" type="textarea" :rows="4" placeholder="请输入课程介绍" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 查看课程详情：管理端页面结构图「课程详情 · 基本信息」 -->
    <el-dialog title="课程详情" v-model="openView" width="680px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="课程名称">{{ viewForm.name }}</el-descriptions-item>
        <el-descriptions-item label="课种">{{ labelOf(COURSE_TYPES, viewForm.type) }}</el-descriptions-item>
        <el-descriptions-item label="课程难度">{{ difficultyLabel(viewForm.difficulty) }}</el-descriptions-item>
        <el-descriptions-item label="课程状态">
          {{ labelOf(COURSE_STATUSES, viewForm.status) }}
        </el-descriptions-item>
        <el-descriptions-item label="单节时长">
          {{ viewForm.durationMin ? viewForm.durationMin + ' 分钟' : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="展示排序">{{ viewForm.sortNo }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ viewForm.createTime }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ viewForm.updateTime }}</el-descriptions-item>
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
import { listCourse, getCourse, addCourse, updateCourse, changeCourseStatus } from "@/api/course/course"

const { proxy } = getCurrentInstance()

/**
 * 枚举映射：接口只返回 code，中文由前端按详细设计 §1.2.2 的映射表展示
 * （§2.1.5：后端不返回翻译文案，避免耦合展示层）
 */
const COURSE_TYPES = [
  { value: 1, label: "团课" },
  { value: 2, label: "精品课" },
  { value: 3, label: "私教课" },
  { value: 4, label: "特色课" }
]
const COURSE_STATUSES = [
  { value: 1, label: "启用" },
  { value: 0, label: "停用" }
]
/** 难度 1~5 星（§1.2.2） */
const DIFFICULTY_LABELS = ["", "1 星", "2 星", "3 星", "4 星", "5 星"]

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
    type: undefined,
    status: undefined
  },
  rules: {
    name: [
      { required: true, message: "课程名称不能为空", trigger: "blur" },
      { max: 64, message: "课程名称长度不能超过 64", trigger: "blur" }
    ],
    type: [{ required: true, message: "课种不能为空", trigger: "change" }],
    difficulty: [{ required: true, type: "number", min: 1, message: "请选择课程难度（1~5 星）", trigger: "change" }]
  }
})

const { form, viewForm, queryParams, rules } = toRefs(data)

/** 枚举取标签 */
function labelOf(options, value) {
  const hit = options.find(item => item.value === value)
  return hit ? hit.label : "-"
}

/** 难度取标签 */
function difficultyLabel(difficulty) {
  return DIFFICULTY_LABELS[difficulty] || "-"
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
    type: undefined,
    difficulty: 1,
    coverUrl: undefined,
    intro: undefined,
    durationMin: undefined,
    sortNo: 0
  }
  proxy.resetForm("courseRef")
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增课程"
}

/** 编辑按钮操作：详情回显（status 不参与编辑，故不放进表单） */
function handleUpdate(row) {
  reset()
  getCourse(row.id).then(response => {
    const detail = response.data
    form.value = {
      id: detail.id,
      name: detail.name,
      type: detail.type,
      difficulty: detail.difficulty,
      coverUrl: detail.coverUrl,
      intro: detail.intro,
      durationMin: detail.durationMin,
      sortNo: detail.sortNo
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

/** 提交按钮：新增与修改都只提交设计约定的字段（不含 status） */
function submitForm() {
  proxy.$refs["courseRef"].validate(valid => {
    if (!valid) {
      return
    }
    const payload = {
      name: form.value.name,
      type: form.value.type,
      difficulty: form.value.difficulty,
      coverUrl: form.value.coverUrl || null,
      intro: form.value.intro || null,
      durationMin: form.value.durationMin === undefined || form.value.durationMin === null ? null : form.value.durationMin,
      sortNo: form.value.sortNo === undefined || form.value.sortNo === null ? 0 : form.value.sortNo
    }
    const request = form.value.id ? updateCourse(form.value.id, payload) : addCourse(payload)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.id ? "修改成功" : "新增成功")
      open.value = false
      getList()
    }).catch(() => {})
  })
}

/** 启用 / 停用：停用被排班或预约引用时，后端返回业务码 409，提示语由统一错误提示弹出 */
function handleStatusChange(row) {
  const targetStatus = row.status === 1 ? 0 : 1
  const actionText = targetStatus === 0 ? "停用" : "启用"
  proxy.$modal.confirm('确认要' + actionText + '课程"' + row.name + '"吗？').then(() => {
    return changeCourseStatus(row.id, targetStatus)
  }).then(() => {
    proxy.$modal.msgSuccess(actionText + "成功")
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

<style scoped>
.form-tip {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
