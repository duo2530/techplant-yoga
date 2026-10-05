<template>
  <div class="app-container">
    <el-form
      ref="queryRef"
      v-show="showSearch"
      :model="queryParams"
      :inline="true"
    >
      <el-form-item label="手机号" prop="phone">
        <el-input
          v-model="queryParams.phone"
          placeholder="支持尾号模糊查询"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="显示名" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="如 忻会员8000"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="会员等级" prop="level">
        <el-select v-model="queryParams.level" placeholder="全部" clearable style="width: 170px">
          <el-option
            v-for="item in MEMBER_LEVELS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增会员</el-button>
      </el-col>
      <el-col :span="12">
        <el-tag type="info">会员档案不设状态位（二期不做停用）；建档不创建登录账号，显示名由系统生成（忻会员 + 手机号后 4 位）</el-tag>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="memberList">
      <el-table-column label="会员显示名" prop="name" min-width="140" />
      <el-table-column label="手机号" prop="phone" width="140" />
      <el-table-column label="性别" prop="genderName" width="90" align="center" />
      <el-table-column label="会员等级" prop="levelName" min-width="130" align="center" />
      <el-table-column label="账号" width="110" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.userId ? 'success' : 'info'">
            {{ scope.row.userId ? '已注册' : '未注册' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="建档时间" prop="createTime" width="180" />
      <el-table-column label="更新时间" prop="updateTime" width="180" />
      <el-table-column label="操作" width="150" fixed="right" align="center">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)">修改</el-button>
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
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

    <el-dialog v-model="open" :title="title" width="560px" append-to-body>
      <el-alert
        :title="form.memberId
          ? '手机号是账号绑定键，二期不支持修改；显示名由系统生成，不提供录入。'
          : '建档不创建登录账号、不设置密码：会员在用户端注册后按手机号自动绑定已有档案。'"
        type="info"
        :closable="false"
        class="mb20"
      />
      <el-form ref="memberRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="form.phone"
            :disabled="!!form.memberId"
            placeholder="请输入 11 位手机号"
            maxlength="11"
          />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-select v-model="form.gender" style="width: 100%">
            <el-option
              v-for="item in MEMBER_GENDERS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="会员等级" prop="level">
          <el-select v-model="form.level" style="width: 100%">
            <el-option
              v-for="item in MEMBER_LEVELS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
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

<script setup name="Member">
import { addMember, delMember, getMember, listMember, updateMember } from '@/api/member/member'

const { proxy } = getCurrentInstance()

// 性别与等级用固定枚举（后端用 Java 枚举、不建字典），码→名由后端成对返回，页面只透传
const MEMBER_GENDERS = [
  { value: 0, label: '未知' },
  { value: 1, label: '男' },
  { value: 2, label: '女' }
]
const MEMBER_LEVELS = [{ value: 0, label: 'V0 普通会员' }]

const memberList = ref([])
const loading = ref(true)
const showSearch = ref(true)
const total = ref(0)
const open = ref(false)
const title = ref('')

const emptyForm = () => ({ memberId: undefined, phone: undefined, gender: 0, level: 0 })

const data = reactive({
  form: emptyForm(),
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    phone: undefined,
    name: undefined,
    level: undefined
  },
  rules: {
    phone: [
      { required: true, message: '手机号不能为空', trigger: 'blur' },
      { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的 11 位手机号', trigger: 'blur' }
    ],
    gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
    level: [{ required: true, message: '请选择会员等级', trigger: 'change' }]
  }
})

const { queryParams, form, rules } = toRefs(data)

function getList() {
  loading.value = true
  listMember(queryParams.value).then(response => {
    memberList.value = response.rows || []
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
  proxy.resetForm('memberRef')
}

function handleAdd() {
  reset()
  title.value = '新增会员（建档）'
  open.value = true
}

function handleUpdate(row) {
  reset()
  const fill = member => {
    form.value = {
      memberId: row.id,
      phone: member.phone,
      gender: Number(member.gender) || 0,
      level: Number(member.level) || 0
    }
    title.value = `修改会员 · ${member.name}`
    open.value = true
  }
  getMember(row.id).then(response => {
    if (response.data) {
      fill(response.data)
      return
    }
    fill(row)
  }).catch(() => fill(row))
}

function cancel() {
  open.value = false
  reset()
}

function submitForm() {
  proxy.$refs.memberRef.validate(valid => {
    if (!valid) {
      return
    }
    // 手机号是绑定键：修改时只提交 gender / level，phone 由服务端忽略
    const payload = { gender: form.value.gender, level: form.value.level }
    const action = form.value.memberId
      ? updateMember(form.value.memberId, payload)
      : addMember({ phone: form.value.phone, ...payload })
    action.then(() => {
      proxy.$modal.msgSuccess(form.value.memberId ? '修改成功' : '建档成功')
      open.value = false
      getList()
    }).catch(error => {
      proxy.$modal.msgError(error?.message || '操作失败')
    })
  })
}

function handleDelete(row) {
  proxy.$modal.confirm(`确认删除会员“${row.name}（${row.phone}）”吗？`).then(() => {
    return delMember(row.id)
  }).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    getList()
  }).catch(error => {
    // 删除被拒时（409）把服务端提示语原样展示，含名下会员卡／预约数量
    if (error !== 'cancel') {
      proxy.$modal.msgError(error?.message || '删除失败')
    }
  })
}

getList()
</script>
