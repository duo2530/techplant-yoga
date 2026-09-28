<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="教练名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入教练名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="教练状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择教练状态" clearable style="width: 160px">
          <el-option label="启用" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
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
      <el-table-column label="教练名称" prop="name" min-width="140" show-overflow-tooltip />
      <el-table-column label="教练头衔" prop="title" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
            {{ scope.row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="230" fixed="right" class-name="small-padding fixed-width">
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

    <el-dialog :title="title" v-model="open" width="680px" append-to-body>
      <el-form ref="coachRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="教练名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入教练名称" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="教练头衔" prop="title">
          <el-input v-model="form.title" placeholder="例如：金牌教练" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="教练头像" prop="avatarUrl">
          <image-upload v-model="form.avatarUrl" :limit="1" />
        </el-form-item>
        <el-form-item label="教练相册" prop="albumUrls">
          <image-upload v-model="form.albumUrls" :limit="5" />
        </el-form-item>
        <el-form-item label="教练简介" prop="intro">
          <el-input v-model="form.intro" type="textarea" :rows="5" maxlength="5000" show-word-limit placeholder="请输入教练简介" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog title="教练详情" v-model="openView" width="720px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="教练名称">{{ viewForm.name }}</el-descriptions-item>
        <el-descriptions-item label="教练头衔">{{ viewForm.title || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ viewForm.status === 1 ? '启用' : '停用' }}</el-descriptions-item>
        <el-descriptions-item label="教练头像">
          <el-image
            v-if="viewForm.avatarUrl"
            :src="viewForm.avatarUrl"
            :preview-src-list="[viewForm.avatarUrl]"
            preview-teleported
            fit="cover"
            style="width: 80px; height: 80px"
          />
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="教练简介" :span="2">
          <span style="white-space: pre-wrap">{{ viewForm.intro || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="教练相册" :span="2">
          <div v-if="viewForm.albumUrls && viewForm.albumUrls.length" class="album-list">
            <el-image
              v-for="url in viewForm.albumUrls"
              :key="url"
              :src="url"
              :preview-src-list="viewForm.albumUrls"
              preview-teleported
              fit="cover"
              style="width: 90px; height: 90px; margin-right: 8px"
            />
          </div>
          <span v-else>-</span>
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

<script setup name="Coach">
import { listCoach, getCoach, addCoach, updateCoach, changeCoachStatus } from "@/api/coach/coach"

const { proxy } = getCurrentInstance()

const coachList = ref([])
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
    status: undefined
  },
  rules: {
    name: [
      { required: true, message: "教练名称不能为空", trigger: "blur" },
      { max: 64, message: "教练名称长度不能超过 64", trigger: "blur" }
    ],
    title: [{ max: 64, message: "教练头衔长度不能超过 64", trigger: "blur" }]
  }
})

const { form, viewForm, queryParams, rules } = toRefs(data)

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

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function reset() {
  form.value = {
    id: undefined,
    intro: undefined,
    name: undefined,
    title: undefined,
    avatarUrl: undefined,
    albumUrls: []
  }
  proxy.resetForm("coachRef")
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "新增教练"
}

function handleUpdate(row) {
  reset()
  getCoach(row.id).then(response => {
    const detail = response.data
    form.value = {
      id: detail.id,
      intro: detail.intro,
      name: detail.name,
      title: detail.title,
      avatarUrl: detail.avatarUrl,
      albumUrls: detail.albumUrls || []
    }
    open.value = true
    title.value = "修改教练"
  })
}

function handleView(row) {
  getCoach(row.id).then(response => {
    viewForm.value = response.data
    openView.value = true
  })
}

function submitForm() {
  proxy.$refs["coachRef"].validate(valid => {
    if (!valid) {
      return
    }
    const payload = {
      intro: form.value.intro || null,
      name: form.value.name,
      title: form.value.title || null,
      avatarUrl: form.value.avatarUrl || null,
      albumUrls: normalizeAlbumUrls(form.value.albumUrls)
    }
    const request = form.value.id ? updateCoach(form.value.id, payload) : addCoach(payload)
    request.then(() => {
      proxy.$modal.msgSuccess(form.value.id ? "修改成功" : "新增成功")
      open.value = false
      getList()
    }).catch(() => {})
  })
}

function normalizeAlbumUrls(value) {
  if (Array.isArray(value)) {
    return value.filter(item => item)
  }
  if (typeof value === "string" && value.trim()) {
    return value.split(",").map(item => item.trim()).filter(item => item)
  }
  return []
}

function handleStatusChange(row) {
  const targetStatus = row.status === 1 ? 0 : 1
  const actionText = targetStatus === 0 ? "停用" : "启用"
  proxy.$modal.confirm('确认要' + actionText + '教练"' + row.name + '"吗？').then(() => {
    return changeCoachStatus(row.id, targetStatus)
  }).then(() => {
    proxy.$modal.msgSuccess(actionText + "成功")
    getList()
  }).catch(() => {})
}

function cancel() {
  open.value = false
  reset()
}

getList()
</script>
