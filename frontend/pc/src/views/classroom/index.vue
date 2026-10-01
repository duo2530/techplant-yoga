<template>
  <div class="app-container">
    <!-- 查询条件：按门店筛选 + 教室名称模糊（教室管理详细设计 §5.3） -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="所属门店" prop="storeId">
        <el-select v-model="queryParams.storeId" placeholder="请选择所属门店" clearable filterable style="width: 220px">
          <el-option v-for="item in storeOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="教室名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入教室名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <!-- 一期不做 RBAC（BR-全局-003）：不挂 v-hasPermi，业务接口只要求登录 -->
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增教室</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="classroomList">
      <el-table-column label="教室名称" prop="name" :show-overflow-tooltip="true" min-width="160" />
      <el-table-column label="所属门店" prop="storeName" min-width="150">
        <template #default="scope">
          <span>{{ scope.row.storeName || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="170" />
      <el-table-column label="更新时间" align="center" prop="updateTime" width="170" />
      <el-table-column label="操作" align="center" width="180" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">改名</el-button>
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

    <!-- 新增 / 改名：编辑弹窗只有一个名称输入框（不出现门店选择器）；换门店等价于删除后重建 -->
    <el-dialog :title="title" v-model="open" width="480px" append-to-body>
      <el-form ref="classroomRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item v-if="!form.id" label="所属门店" prop="storeId">
          <el-select v-model="form.storeId" placeholder="请选择所属门店" filterable style="width: 100%">
            <el-option v-for="item in storeOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教室名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入教室名称" maxlength="32" show-word-limit />
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

<script setup name="Classroom">
import { listClassroom, addClassroom, updateClassroom, delClassroom } from "@/api/classroom/classroom"
import { listStore } from "@/api/store/store"

const { proxy } = getCurrentInstance()

const classroomList = ref([])
const storeOptions = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const title = ref("")

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    storeId: undefined,
    name: undefined
  },
  rules: {
    storeId: [{ required: true, message: "请选择所属门店", trigger: "change" }],
    name: [
      { required: true, message: "教室名称不能为空", trigger: "blur" },
      { max: 32, message: "教室名称长度不能超过 32", trigger: "blur" }
    ]
  }
})

const { form, queryParams, rules } = toRefs(data)

/** 查询教室列表 */
function getList() {
  loading.value = true
  listClassroom(queryParams.value).then(response => {
    classroomList.value = response.rows
    total.value = response.total
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

/** 门店下拉（新增时选择所属门店；教室改名不需要换门店） */
function getStoreOptions() {
  listStore({ pageNum: 1, pageSize: 100 }).then(response => {
    storeOptions.value = response.rows || []
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
    storeId: undefined,
    name: undefined
  }
  proxy.resetForm("classroomRef")
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增教室"
}

/** 改名按钮操作：教室没有详情接口，直接用列表行数据回填 */
function handleUpdate(row) {
  reset()
  form.value = {
    id: row.id,
    storeId: row.storeId,
    name: row.name
  }
  open.value = true
  title.value = "修改教室名称"
}

/** 提交按钮：新增提交 { storeId, name }；改名只提交 { name }（DTO 里没有 storeId） */
function submitForm() {
  proxy.$refs["classroomRef"].validate(valid => {
    if (!valid) {
      return
    }
    const payload = form.value.id
      ? { name: form.value.name }
      : { storeId: form.value.storeId, name: form.value.name }
    const request = form.value.id ? updateClassroom(form.value.id, payload) : addClassroom(payload)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.id ? "修改成功" : "新增成功")
      open.value = false
      getList()
    }).catch(() => {})
  })
}

/** 删除按钮操作：被未结束排课引用时后端返回 409，提示语由 axios 拦截器统一弹出 */
function handleDelete(row) {
  proxy.$modal.confirm('确认要删除教室"' + row.name + '"吗？').then(() => {
    return delClassroom(row.id)
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

getStoreOptions()
getList()
</script>
