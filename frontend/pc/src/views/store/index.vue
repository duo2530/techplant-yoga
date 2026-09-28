<template>
  <div class="app-container">
    <el-form
      ref="queryRef"
      v-show="showSearch"
      :model="queryParams"
      :inline="true"
    >
      <el-form-item label="门店名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入门店名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="所在区域" prop="region">
        <el-input
          v-model="queryParams.region"
          placeholder="请输入所在区域"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="经营类型" prop="businessType">
        <el-select v-model="queryParams.businessType" placeholder="请选择" clearable style="width: 150px">
          <el-option label="直营连锁" :value="1" />
          <el-option label="加盟" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="门店类型" prop="storeType">
        <el-select v-model="queryParams.storeType" placeholder="请选择" clearable style="width: 150px">
          <el-option label="主力店" :value="1" />
          <el-option label="精品店" :value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择" clearable style="width: 120px">
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
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增门店</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="storeList">
      <el-table-column label="门店名称" prop="name" min-width="160" />
      <el-table-column label="所在区域" prop="region" min-width="140" />
      <el-table-column label="门店地址" prop="address" min-width="220" show-overflow-tooltip />
      <el-table-column label="门店电话" prop="phone" width="140" />
      <el-table-column label="经营类型" prop="businessType" width="100">
        <template #default="scope">
          {{ businessTypeLabel(scope.row.businessType) }}
        </template>
      </el-table-column>
      <el-table-column label="门店类型" prop="storeType" width="100">
        <template #default="scope">
          {{ storeTypeLabel(scope.row.storeType) }}
        </template>
      </el-table-column>
      <el-table-column label="营业时间" prop="businessHours" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" prop="status" width="90" align="center">
        <template #default="scope">
          <el-switch
            v-model="scope.row.status"
            :active-value="1"
            :inactive-value="0"
            @change="handleStatus(scope.row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" prop="updateTime" width="180">
        <template #default="scope">
          {{ parseTime(scope.row.updateTime || scope.row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right" align="center">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
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

    <el-dialog v-model="open" :title="title" width="760px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="105px">
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="门店名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入门店名称" maxlength="64" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="行政区划" prop="areaPath">
              <el-cascader
                v-model="form.areaPath"
                :options="areaOptions"
                :props="areaProps"
                clearable
                filterable
                style="width: 100%"
                placeholder="请选择省 / 市 / 区"
                @change="handleAreaChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所在区域">
              <el-input :model-value="form.region || '选择行政区划后自动生成'" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="门店地址" prop="address">
              <el-input v-model="form.address" placeholder="请输入详细门店地址" maxlength="255" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="门店电话" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入门店电话" maxlength="32" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="经营类型" prop="businessType">
              <el-select v-model="form.businessType" style="width: 100%">
                <el-option label="直营连锁" :value="1" />
                <el-option label="加盟" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="门店类型" prop="storeType">
              <el-select v-model="form.storeType" style="width: 100%">
                <el-option label="主力店" :value="1" />
                <el-option label="精品店" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="营业时间" prop="businessHours">
              <el-input v-model="form.businessHours" placeholder="如：周一至周日 09:00-22:00" maxlength="64" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
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
import { addStore, getStore, listStore, updateStore, updateStoreStatus } from '@/api/store/store'
import areaData from '@/data/china-area-data.json'

const { proxy } = getCurrentInstance()

const storeList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const open = ref(false)
const title = ref('')

const areaProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: true
}

const areaOptions = buildAreaOptions()

const emptyForm = () => ({
  storeId: undefined,
  areaPath: [],
  name: undefined,
  region: undefined,
  provinceCode: undefined,
  cityCode: undefined,
  districtCode: undefined,
  address: undefined,
  phone: undefined,
  businessType: 1,
  storeType: 1,
  businessHours: undefined
})

const data = reactive({
  form: emptyForm(),
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: undefined,
    region: undefined,
    businessType: undefined,
    storeType: undefined,
    status: undefined
  },
  rules: {
    name: [{ required: true, message: '门店名称不能为空', trigger: 'blur' }],
    areaPath: [{ type: 'array', required: true, message: '请选择省、市、区', trigger: 'change' }],
    address: [{ required: true, message: '门店地址不能为空', trigger: 'blur' }],
    phone: [{ required: true, message: '门店电话不能为空', trigger: 'blur' }],
    businessType: [{ required: true, message: '经营类型不能为空', trigger: 'change' }],
    storeType: [{ required: true, message: '门店类型不能为空', trigger: 'change' }],
    businessHours: [{ required: true, message: '营业时间不能为空', trigger: 'blur' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

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

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  handleQuery()
}

function reset() {
  form.value = emptyForm()
  proxy.resetForm('formRef')
}

function handleAdd() {
  reset()
  title.value = '新增门店'
  open.value = true
}

function handleUpdate(row) {
  reset()
  getStore(row.id).then(response => {
    form.value = { ...response.data, areaPath: buildAreaPath(response.data), storeId: row.id }
    title.value = '修改门店'
    open.value = true
  })
}

function cancel() {
  open.value = false
  reset()
}

function submitForm() {
  proxy.$refs.formRef.validate(valid => {
    if (!valid) {
      return
    }
    const payload = { ...form.value }
    delete payload.storeId
    delete payload.areaPath
    const action = form.value.storeId
      ? updateStore(form.value.storeId, payload)
      : addStore(payload)
    action.then(() => {
      proxy.$modal.msgSuccess(form.value.storeId ? '修改成功' : '新增成功')
      open.value = false
      getList()
    })
  })
}

function handleAreaChange(path) {
  const values = Array.isArray(path) ? path : []
  const labels = findAreaLabels(values)
  form.value.provinceCode = values[0] ? String(values[0]) : undefined
  form.value.cityCode = values[1] ? String(values[1]) : undefined
  form.value.districtCode = values[2] ? String(values[2]) : undefined
  form.value.region = labels.join('') || undefined
}

function handleStatus(row) {
  const nextStatus = row.status
  const oldStatus = nextStatus === 1 ? 0 : 1
  proxy.$modal.confirm(`确认${nextStatus === 1 ? '启用' : '停用'}门店“${row.name}”吗？`).then(() => {
    return updateStoreStatus(row.id, nextStatus)
  }).then(() => {
    proxy.$modal.msgSuccess(nextStatus === 1 ? '启用成功' : '停用成功')
  }).catch(() => {
    row.status = oldStatus
  })
}

function businessTypeLabel(value) {
  return Number(value) === 1 ? '直营连锁' : '加盟'
}

function storeTypeLabel(value) {
  return Number(value) === 1 ? '主力店' : '精品店'
}

function buildAreaOptions() {
  const provinces = areaData['86'] || {}
  return Object.entries(provinces).map(([provinceCode, provinceName]) => {
    const cities = areaData[provinceCode] || {}
    const cityOptions = Object.entries(cities).map(([cityCode, cityName]) => ({
      value: cityCode,
      label: cityName,
      children: buildDistrictOptions(cityCode)
    }))

    // 直辖市的中间层通常叫“市辖区”，界面改成城市名称，保留数据中的 cityCode，
    // 让表单始终保持省 / 市 / 区三级，并兼容后端现有的 cityCode 字段。
    if (['110000', '120000', '310000', '500000'].includes(provinceCode)) {
      const municipalityCityCode = Object.keys(cities)[0]
      return {
        value: provinceCode,
        label: provinceName,
        children: [{
          value: municipalityCityCode,
          label: provinceName,
          children: buildDistrictOptions(municipalityCityCode)
        }]
      }
    }

    return { value: provinceCode, label: provinceName, children: cityOptions }
  })
}

function buildDistrictOptions(cityCode) {
  const districts = areaData[cityCode] || {}
  const entries = Object.entries(districts)
  if (entries.length) {
    return entries.map(([districtCode, districtName]) => ({
      value: districtCode,
      label: districtName
    }))
  }
  // 东莞、中山等无下级区县的城市，用城市自身作为末级 code，保证后端字段完整。
  return [{ value: cityCode, label: '市辖区域' }]
}

function buildAreaPath(store) {
  if (!store || !store.provinceCode || !store.cityCode || !store.districtCode) {
    return []
  }
  return [String(store.provinceCode), String(store.cityCode), String(store.districtCode)]
}

function findAreaLabels(values) {
  const labels = []
  let options = areaOptions
  values.forEach(value => {
    const option = options.find(item => String(item.value) === String(value))
    if (!option) {
      return
    }
    labels.push(option.label)
    options = option.children || []
  })
  return labels
}

getList()
</script>
