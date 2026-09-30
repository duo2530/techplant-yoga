<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch">
      <el-form-item label="会员" prop="memberName"><el-input v-model="queryParams.memberName" placeholder="会员昵称" clearable @keyup.enter="handleQuery" /></el-form-item>
      <el-form-item label="手机号" prop="phone"><el-input v-model="queryParams.phone" placeholder="手机号" clearable @keyup.enter="handleQuery" /></el-form-item>
      <el-form-item label="卡片名称" prop="cardName"><el-input v-model="queryParams.cardName" placeholder="卡片名称" clearable @keyup.enter="handleQuery" /></el-form-item>
      <el-form-item label="卡号" prop="cardNo"><el-input v-model="queryParams.cardNo" placeholder="卡号" clearable @keyup.enter="handleQuery" /></el-form-item>
      <el-form-item label="卡片类型" prop="cardType">
        <el-select v-model="queryParams.cardType" placeholder="全部" clearable style="width: 130px"><el-option label="次数卡" :value="1" /><el-option label="期限卡" :value="2" /></el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px"><el-option label="未激活" :value="0" /><el-option label="已激活" :value="1" /><el-option label="停用" :value="2" /></el-select>
      </el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button type="primary" plain icon="Plus" @click="handleAdd">开卡</el-button></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="cardList">
      <el-table-column label="会员" prop="memberName" min-width="110" />
      <el-table-column label="卡号" prop="cardNo" min-width="190" />
      <el-table-column label="开卡门店" prop="storeName" min-width="150" />
      <el-table-column label="手机号" prop="phone" width="140" />
      <el-table-column label="卡片名称" prop="cardName" min-width="150" />
      <el-table-column label="类型" width="90" align="center"><template #default="scope">{{ cardTypeLabel(scope.row.cardType) }}</template></el-table-column>
      <el-table-column label="适用课种" min-width="130"><template #default="scope">{{ courseScopeLabel(scope.row.courseScope) }}</template></el-table-column>
      <el-table-column label="次数" width="110" align="center"><template #default="scope">{{ countText(scope.row) }}</template></el-table-column>
      <el-table-column label="有效期" min-width="180"><template #default="scope">{{ validityText(scope.row) }}</template></el-table-column>
      <el-table-column label="状态" width="100" align="center"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button v-if="scope.row.status === 0" link type="success" icon="CircleCheck" @click="handleActivate(scope.row)">激活</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="open" title="会员开卡" width="620px" append-to-body>
      <el-alert title="新卡创建后为未激活状态，需要在列表中点击激活后才能用于预约。" type="info" :closable="false" class="mb20" />
      <el-form ref="cardRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属会员" prop="memberId"><el-select v-model="form.memberId" filterable placeholder="请选择会员" style="width:100%"><el-option v-for="item in memberOptions" :key="item.id" :label="`${item.nickname}（${item.phone}）`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="开卡门店" prop="storeId"><el-select v-model="form.storeId" filterable placeholder="请选择门店" style="width:100%"><el-option v-for="item in storeOptions" :key="item.id" :label="`${item.name}（${item.storeNo}）`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="卡片名称" prop="cardName"><el-input v-model="form.cardName" placeholder="例如：普拉提 20 次卡" /></el-form-item>
        <el-form-item label="卡片类型" prop="cardType"><el-radio-group v-model="form.cardType" @change="handleCardTypeChange"><el-radio :value="1">次数卡</el-radio><el-radio :value="2">期限卡</el-radio></el-radio-group></el-form-item>
        <el-form-item label="适用课种" prop="courseScope"><el-select v-model="form.courseScope" style="width:100%"><el-option v-for="item in COURSE_SCOPES" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item v-if="form.cardType === 1" label="初始次数" prop="initialCount"><el-input-number v-model="form.initialCount" :min="1" :max="999" /></el-form-item>
        <el-form-item label="有效天数" prop="validDays"><el-select v-model="form.validDays" style="width:100%"><el-option label="30 天（月卡）" :value="30" /><el-option label="365 天（年卡）" :value="365" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="submitForm">确认开卡</el-button><el-button @click="open=false">取消</el-button></template>
    </el-dialog>

    <el-dialog v-model="openView" title="会员卡详情" width="680px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="卡号">{{ detail.cardNo }}</el-descriptions-item><el-descriptions-item label="开卡门店">{{ detail.storeName }}（{{ detail.storeNo }}）</el-descriptions-item>
        <el-descriptions-item label="会员">{{ detail.memberName }}</el-descriptions-item><el-descriptions-item label="手机号">{{ detail.phone }}</el-descriptions-item>
        <el-descriptions-item label="卡片名称">{{ detail.cardName }}</el-descriptions-item><el-descriptions-item label="卡片类型">{{ cardTypeLabel(detail.cardType) }}</el-descriptions-item>
        <el-descriptions-item label="适用课种">{{ courseScopeLabel(detail.courseScope) }}</el-descriptions-item><el-descriptions-item label="卡片状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="初始次数">{{ detail.cardType === 1 ? detail.initialCount : '-' }}</el-descriptions-item><el-descriptions-item label="剩余次数">{{ detail.cardType === 1 ? detail.remainingCount : '-' }}</el-descriptions-item>
        <el-descriptions-item label="激活时间">{{ detail.activateTime || '-' }}</el-descriptions-item><el-descriptions-item label="有效期">{{ validityText(detail) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button @click="openView=false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="MemberCard">
import { activateMemberCard, addMemberCard, getMemberCard, listMemberCard } from '@/api/memberCard/memberCard'
import { listMember } from '@/api/member/member'
import { listStore } from '@/api/store/store'

const { proxy } = getCurrentInstance()
const loading = ref(false), showSearch = ref(true), open = ref(false), openView = ref(false)
const cardList = ref([]), memberOptions = ref([]), storeOptions = ref([]), total = ref(0), detail = ref({})
const COURSE_SCOPES = [{ value: 1, label: '团课' }, { value: 2, label: '精品课' }, { value: 3, label: '特色课' }, { value: 4, label: '私教课' }]
const queryParams = reactive({ pageNum: 1, pageSize: 10, memberName: undefined, phone: undefined, cardName: undefined, cardNo: undefined, cardType: undefined, status: undefined })
const emptyForm = () => ({ memberId: undefined, storeId: undefined, cardName: '', cardType: 1, courseScope: 1, initialCount: 10, validDays: 365 })
const form = ref(emptyForm())
const rules = { memberId: [{ required: true, message: '请选择会员', trigger: 'change' }], storeId: [{ required: true, message: '请选择开卡门店', trigger: 'change' }], cardName: [{ required: true, message: '请输入卡片名称', trigger: 'blur' }], cardType: [{ required: true, message: '请选择卡片类型', trigger: 'change' }], courseScope: [{ required: true, message: '请选择适用课种', trigger: 'change' }], initialCount: [{ required: true, message: '请输入初始次数', trigger: 'change' }], validDays: [{ required: true, message: '请选择有效天数', trigger: 'change' }] }

function cardTypeLabel(value) { return Number(value) === 1 ? '次数卡' : '期限卡' }
function courseScopeLabel(value) { return COURSE_SCOPES.find(item => item.value === Number(value))?.label || '-' }
function statusLabel(value) { return ['未激活', '已激活', '停用'][Number(value)] || '-' }
function statusType(value) { return Number(value) === 1 ? 'success' : Number(value) === 0 ? 'warning' : 'info' }
function countText(row) { return Number(row.cardType) === 1 ? `${row.remainingCount} / ${row.initialCount}` : '-' }
function validityText(row) { return row.startDate && row.endDate ? `${row.startDate} 至 ${row.endDate}` : '激活后生效' }
function getList() { loading.value=true; listMemberCard(queryParams).then(res => { cardList.value=res.rows; total.value=res.total }).finally(() => { loading.value=false }) }
function loadMembers() { Promise.all([listMember({ pageNum:1, pageSize:100, status:1 }),listStore({pageNum:1,pageSize:100,status:1})]).then(([members,stores]) => { memberOptions.value=members.rows;storeOptions.value=stores.rows }) }
function handleQuery() { queryParams.pageNum=1; getList() }
function resetQuery() { proxy.resetForm('queryRef'); handleQuery() }
function handleAdd() { form.value=emptyForm(); open.value=true; nextTick(() => proxy.$refs.cardRef?.clearValidate()) }
function handleCardTypeChange(value) { if (value === 2) form.value.initialCount=null }
function submitForm() { proxy.$refs.cardRef.validate(valid => { if (!valid) return; addMemberCard(form.value).then(() => { proxy.$modal.msgSuccess('开卡成功'); open.value=false; getList() }).catch(error => proxy.$modal.msgError(error.message)) }) }
function handleView(row) { getMemberCard(row.id).then(res => { detail.value=res.data; openView.value=true }) }
function handleActivate(row) { proxy.$modal.confirm(`确认激活“${row.cardName}”吗？`).then(() => activateMemberCard(row.id)).then(() => { proxy.$modal.msgSuccess('激活成功'); getList() }).catch(() => {}) }

loadMembers(); getList()
</script>
