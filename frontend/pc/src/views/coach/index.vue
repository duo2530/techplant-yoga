<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="教练姓名" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入教练姓名"
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增教练</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="coachList">
      <el-table-column label="头像" align="center" width="90">
        <template #default="scope">
          <el-image
            v-if="scope.row.avatarUrl"
            :src="scope.row.avatarUrl"
            :preview-src-list="[scope.row.avatarUrl]"
            preview-teleported
            fit="cover"
            style="width: 40px; height: 40px"
          />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="姓名" prop="name" min-width="140" show-overflow-tooltip />
      <el-table-column label="联系电话" prop="phone" min-width="140" show-overflow-tooltip />
      <el-table-column label="更新时间" align="center" prop="updateTime" width="180">
        <template #default="scope">
          <span>{{ parseTime(scope.row.updateTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="180" fixed="right" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
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

    <el-dialog :title="title" v-model="open" width="680px" append-to-body>
      <el-form ref="coachRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入教练姓名" maxlength="32" show-word-limit />
        </el-form-item>
        <el-form-item label="头像" prop="avatarUrl">
          <image-upload v-model="form.avatarUrl" :limit="1" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入联系电话" maxlength="32" show-word-limit />
        </el-form-item>
        <el-form-item label="简介" prop="intro">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="4"
            maxlength="512"
            show-word-limit
            placeholder="请输入教练简介"
          />
        </el-form-item>
        <el-form-item label="相册" prop="gallery">
          <image-upload v-model="form.gallery" :limit="5" />
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

<script setup name="Coach">
import { listCoach, getCoach, addCoach, updateCoach, delCoach } from "@/api/coach/coach"

const { proxy } = getCurrentInstance()

const coachList = ref([])
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
    name: undefined
  },
  rules: {
    name: [
      { required: true, message: "教练姓名不能为空", trigger: "blur" },
      { max: 32, message: "教练姓名长度不能超过 32", trigger: "blur" }
    ],
    phone: [{ max: 32, message: "教练联系电话长度不能超过 32", trigger: "blur" }],
    intro: [{ max: 512, message: "教练简介长度不能超过 512", trigger: "blur" }]
  }
})

const { form, queryParams, rules } = toRefs(data)

/** 查询教练列表 */
function getList() {
  loading.value = true
  listCoach(queryParams.value).then(response => {
    coachList.value = response.rows
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
    avatarUrl: undefined,
    phone: undefined,
    intro: undefined,
    gallery: []
  }
  proxy.resetForm("coachRef")
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增教练"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  getCoach(row.id).then(response => {
    const detail = response.data
    form.value = {
      id: detail.id,
      name: detail.name,
      avatarUrl: detail.avatarUrl,
      phone: detail.phone,
      intro: detail.intro,
      gallery: Array.isArray(detail.gallery) ? detail.gallery : []
    }
    open.value = true
    title.value = "修改教练"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["coachRef"].validate(valid => {
    if (!valid) {
      return
    }
    const payload = {
      name: form.value.name,
      avatarUrl: form.value.avatarUrl || null,
      phone: form.value.phone || null,
      intro: form.value.intro || null,
      gallery: normalizeGallery(form.value.gallery)
    }
    const request = form.value.id ? updateCoach(form.value.id, payload) : addCoach(payload)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.id ? "修改成功" : "新增成功")
      open.value = false
      getList()
    }).catch(() => {})
  })
}

/**
 * image-upload 回传的是逗号分隔字符串，详情接口返回的是数组；
 * 统一收敛为后端要的 List<String>。
 */
function normalizeGallery(value) {
  if (Array.isArray(value)) {
    return value.filter(item => item)
  }
  if (typeof value === "string" && value.trim()) {
    return value.split(",").map(item => item.trim()).filter(item => item)
  }
  return []
}

/** 删除按钮操作（删除被拒时由响应拦截器统一提示服务端 msg，如「该教练仍有 N 节未结束的排课，无法删除」） */
function handleDelete(row) {
  proxy.$modal.confirm('是否确认删除教练"' + row.name + '"？').then(() => {
    return delCoach(row.id)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

getList()
</script>
