<template>
  <div class="app-container">
    <!-- 查询条件：手机号后四位精确 / 显示名模糊 / 会员等级精确 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="手机号后四位" prop="phone_last4">
        <el-input
          v-model="queryParams.phone_last4"
          placeholder="4 位数字"
          clearable
          maxlength="4"
          style="width: 140px"
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
          <el-option v-for="dict in member_level" :key="dict.value" :label="dict.label" :value="Number(dict.value)" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <!-- 本期不做 RBAC：业务接口只要求登录，不挂 v-hasPermi -->
        <el-button type="primary" plain icon="Plus" @click="handleAdd">新增会员</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="memberList">
      <el-table-column label="会员显示名" prop="name" :show-overflow-tooltip="true" min-width="140" />
      <el-table-column label="手机号" prop="phone" width="140" />
      <el-table-column label="性别" prop="genderText" width="90" align="center" />
      <el-table-column label="会员等级" prop="levelText" min-width="130" align="center" />
      <el-table-column label="账号" width="110" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.registered ? 'success' : 'info'">
            {{ scope.row.registered ? '已注册' : '未注册' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="建档时间" align="center" prop="createTime" width="170" />
      <el-table-column label="更新时间" align="center" prop="updateTime" width="170" />
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

    <!-- 新增：只建档、不设密码；修改：手机号是账号绑定键，不可修改 -->
    <el-dialog :title="title" v-model="open" width="520px" append-to-body>
      <el-alert
        :title="form.id
          ? '手机号是账号绑定键，本期不支持修改；显示名由系统生成，不提供录入。'
          : '建档不创建登录账号、不设置密码：会员在用户端注册后按手机号自动绑定已有档案。'"
        type="info"
        :closable="false"
        class="mb20"
      />
      <el-form ref="memberRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="form.phone"
            :disabled="!!form.id"
            placeholder="请输入 11 位手机号"
            maxlength="11"
          />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-select v-model="form.gender" style="width: 100%">
            <el-option v-for="item in MEMBER_GENDERS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="会员等级" prop="level">
          <el-select v-model="form.level" style="width: 100%">
            <el-option
              v-for="item in levelOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
              :disabled="item.disabled"
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
import { listMember, getMember, addMember, updateMember, delMember } from "@/api/member/member"

const { proxy } = getCurrentInstance()

// 性别是后端固定枚举（不建字典），页面只提供录入选项；
// 会员等级取值来自字典 member_level（0~4），下拉与后端 levelText 同一来源
const { member_level } = useDict("member_level")

const MEMBER_GENDERS = [
  { value: 0, label: "未知" },
  { value: 1, label: "男" },
  { value: 2, label: "女" }
]

const memberList = ref([])
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
    phone_last4: undefined,
    name: undefined,
    level: undefined
  },
  rules: {
    phone: [
      { required: true, message: "手机号不能为空", trigger: "blur" },
      { pattern: /^1[3-9]\d{9}$/, message: "请输入正确的 11 位手机号", trigger: "blur" }
    ],
    gender: [{ required: true, message: "请选择性别", trigger: "change" }],
    level: [{ required: true, message: "请选择会员等级", trigger: "change" }]
  }
})

const { form, queryParams, rules } = toRefs(data)

/**
 * 等级下拉：字典中的等级；会员所属等级已被字典停用时保留可见但不可再选，
 * 避免仅修改性别就被迫更换等级（省略的等级由服务端保留原值）
 */
const levelOptions = computed(() => {
  const options = member_level.value.map(dict => ({ value: Number(dict.value), label: dict.label, disabled: false }))
  if (form.value.level !== undefined && form.value.level !== null && !options.some(item => item.value === form.value.level)) {
    options.push({ value: form.value.level, label: `等级 ${form.value.level}（字典已停用）`, disabled: true })
  }
  return options
})

/** 查询会员列表 */
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
    phone: undefined,
    gender: 0,
    level: 0,
    originGender: undefined,
    originLevel: undefined
  }
  proxy.resetForm("memberRef")
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "新增会员"
}

/** 修改按钮操作：先取详情（含 registered 与最新资料），取不到时用列表行回填 */
function handleUpdate(row) {
  reset()
  const fill = member => {
    const gender = Number(member.gender) || 0
    const level = Number(member.level) || 0
    form.value = {
      id: row.id,
      phone: member.phone,
      gender,
      level,
      originGender: gender,
      originLevel: level
    }
    open.value = true
    title.value = "修改会员 · " + (member.name || row.name || "")
  }
  getMember(row.id).then(response => {
    fill(response.data || row)
  }).catch(() => {
    fill(row)
  })
}

/**
 * 提交按钮：新增提交 { phone, gender, level }；
 * 修改只提交实际改动的字段，省略的字段由服务端保留原值
 */
function submitForm() {
  proxy.$refs["memberRef"].validate(valid => {
    if (!valid) {
      return
    }
    if (!form.value.id) {
      addMember({ phone: form.value.phone, gender: form.value.gender, level: form.value.level }).then(() => {
        proxy.$modal.msgSuccess("建档成功")
        open.value = false
        getList()
      }).catch(() => {})
      return
    }

    const payload = {}
    if (form.value.gender !== form.value.originGender) {
      payload.gender = form.value.gender
    }
    if (form.value.level !== form.value.originLevel) {
      payload.level = form.value.level
    }
    if (Object.keys(payload).length === 0) {
      proxy.$modal.msgWarning("未修改任何字段")
      return
    }
    updateMember(form.value.id, payload).then(() => {
      proxy.$modal.msgSuccess("修改成功")
      open.value = false
      getList()
    }).catch(() => {})
  })
}

/** 删除按钮操作：删除后登录凭证随行删除，失败提示由 axios 拦截器统一弹出 */
function handleDelete(row) {
  proxy.$modal.confirm('确认要删除会员"' + row.name + "（" + row.phone + '）"吗？').then(() => {
    return delMember(row.id)
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
