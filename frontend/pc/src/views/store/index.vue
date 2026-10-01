<template>
  <div class="app-container">
    <!-- 查询：门店名称 / 所在区域（字典 store_region 下拉）/ 门店类型（主力店·精品店） -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="门店名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入门店名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="所在区域" prop="regionCode">
        <el-select v-model="queryParams.regionCode" placeholder="请选择所在区域" clearable style="width: 180px">
          <el-option v-for="dict in store_region" :key="dict.value" :label="dict.label" :value="dict.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="门店类型" prop="storeType">
        <el-select v-model="queryParams.storeType" placeholder="请选择门店类型" clearable style="width: 150px">
          <el-option v-for="item in STORE_TYPES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增门店</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="storeList">
      <el-table-column label="图片" align="center" width="90">
        <template #default="scope">
          <el-image
            v-if="scope.row.imageUrl"
            :src="scope.row.imageUrl"
            :preview-src-list="[scope.row.imageUrl]"
            preview-teleported
            fit="cover"
            style="width: 44px; height: 44px"
          />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="门店名称" align="center" prop="name" min-width="140" show-overflow-tooltip />
      <!-- 所在区域用后端返回的 regionName，不在前端做 code → 区名翻译 -->
      <el-table-column label="所在区域" align="center" prop="regionName" width="110" />
      <el-table-column label="地址" align="center" prop="address" min-width="200" show-overflow-tooltip />
      <el-table-column label="电话" align="center" prop="phone" width="140" />
      <!-- 门店类型用后端返回的 storeTypeName（枚举成对返回） -->
      <el-table-column label="门店类型" align="center" prop="storeTypeName" width="100" />
      <el-table-column label="营业时间" align="center" prop="businessHours" min-width="170" show-overflow-tooltip />
      <el-table-column label="更新时间" align="center" prop="updateTime" width="170" />
      <el-table-column label="操作" align="center" width="150" fixed="right" class-name="small-padding fixed-width">
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

    <!-- 新增 / 修改门店：一期没有状态、经营类型、联系人，也没有任何行政区划级联 -->
    <el-dialog :title="title" v-model="open" width="680px" append-to-body>
      <el-form ref="storeRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="门店名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入门店名称" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="门店类型" prop="storeType">
          <el-select v-model="form.storeType" placeholder="请选择门店类型" style="width: 100%">
            <el-option v-for="item in STORE_TYPES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="所在区域" prop="regionCode">
          <el-select v-model="form.regionCode" placeholder="请选择所在区域" filterable style="width: 100%">
            <el-option v-for="dict in store_region" :key="dict.value" :label="dict.label" :value="dict.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入联系电话" maxlength="32" />
        </el-form-item>
        <el-form-item label="营业时间" prop="businessHours">
          <el-input
            v-model="form.businessHours"
            placeholder="如：周一至周日 09:00-22:00"
            maxlength="64"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model="form.address" placeholder="请输入门店地址" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="门店图片" prop="imageUrl">
          <image-upload v-model="form.imageUrl" :limit="1" />
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

<script setup name="Store">
import { listStore, getStore, addStore, updateStore, delStore } from '@/api/store/store'

const { proxy } = getCurrentInstance()
// 所在区域用字典下拉（16 个区），不允许手填；门店类型是固定两值枚举，没有对应字典
const { store_region } = useDict('store_region')
const STORE_TYPES = [
  { value: 1, label: '主力店' },
  { value: 2, label: '精品店' }
]

const storeList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const open = ref(false)
const title = ref('')

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: undefined,
    regionCode: undefined,
    storeType: undefined
  },
  rules: {
    name: [{ required: true, message: '门店名称不能为空', trigger: 'blur' }],
    storeType: [{ required: true, message: '门店类型不能为空', trigger: 'change' }],
    regionCode: [{ required: true, message: '所在区域不能为空', trigger: 'change' }],
    phone: [{ required: true, message: '联系电话不能为空', trigger: 'blur' }],
    businessHours: [{ required: true, message: '营业时间不能为空', trigger: 'blur' }],
    address: [{ required: true, message: '地址不能为空', trigger: 'blur' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询门店列表 */
function getList() {
  loading.value = true
  listStore(queryParams.value).then(response => {
    storeList.value = response.rows || []
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
  proxy.resetForm('queryRef')
  handleQuery()
}

/** 表单重置 */
function reset() {
  form.value = {
    id: undefined,
    name: undefined,
    storeType: 1,
    regionCode: undefined,
    phone: undefined,
    businessHours: undefined,
    address: undefined,
    imageUrl: undefined
  }
  proxy.resetForm('storeRef')
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  title.value = '新增门店'
  open.value = true
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  getStore(row.id).then(response => {
    form.value = response.data
    title.value = '修改门店'
    open.value = true
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 提交按钮：只提交 7 个业务字段，不提交审计字段与派生字段 */
function submitForm() {
  proxy.$refs['storeRef'].validate(valid => {
    if (!valid) {
      return
    }
    const payload = {
      name: form.value.name,
      storeType: form.value.storeType,
      regionCode: form.value.regionCode,
      phone: form.value.phone,
      businessHours: form.value.businessHours,
      address: form.value.address,
      imageUrl: form.value.imageUrl
    }
    if (form.value.id != undefined) {
      updateStore(form.value.id, payload).then(() => {
        proxy.$modal.msgSuccess('修改成功')
        open.value = false
        getList()
      })
    } else {
      addStore(payload).then(() => {
        proxy.$modal.msgSuccess('新增成功')
        open.value = false
        getList()
      })
    }
  })
}

/** 删除按钮操作：被引用时后端返回 409，拦截器已用服务端 msg 原样提示，这里不重复弹出 */
function handleDelete(row) {
  proxy.$modal.confirm('是否确认删除门店"' + row.name + '"？').then(function() {
    return delStore(row.id)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

getList()
</script>
