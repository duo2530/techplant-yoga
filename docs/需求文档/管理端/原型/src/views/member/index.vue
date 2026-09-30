<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch">
      <el-form-item label="会员昵称" prop="nickname">
        <el-input v-model="queryParams.nickname" placeholder="请输入会员昵称" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="queryParams.phone" placeholder="请输入手机号" clearable @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="会员状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="正常" :value="1" />
          <el-option label="停用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-tag type="info">新会员默认等级为 0</el-tag></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="memberList">
      <el-table-column label="会员昵称" prop="nickname" min-width="140" />
      <el-table-column label="手机号" prop="phone" width="150" />
      <el-table-column label="会员等级" width="110" align="center">
        <template #default="scope">等级 {{ scope.row.level }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ memberStatus(scope.row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="加入时间" prop="joinTime" width="180" />
      <el-table-column label="最近活跃" prop="lastActiveTime" width="180" />
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="scope"><el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button></template>
      </el-table-column>
    </el-table>

    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="openView" title="会员详情" width="760px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="会员昵称">{{ detail.nickname }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.phone }}</el-descriptions-item>
        <el-descriptions-item label="会员等级">等级 {{ detail.level }}</el-descriptions-item>
        <el-descriptions-item label="会员状态">{{ memberStatus(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="加入时间">{{ detail.joinTime }}</el-descriptions-item>
        <el-descriptions-item label="最近活跃">{{ detail.lastActiveTime }}</el-descriptions-item>
      </el-descriptions>
      <h4>关联会员卡</h4>
      <el-table :data="detail.cards || []" border>
        <el-table-column label="卡号" prop="cardNo" min-width="190" />
        <el-table-column label="开卡门店" prop="storeName" min-width="150" />
        <el-table-column label="卡片名称" prop="cardName" min-width="160" />
        <el-table-column label="卡片类型" width="100"><template #default="scope">{{ cardType(scope.row.cardType) }}</template></el-table-column>
        <el-table-column label="适用课种" min-width="140"><template #default="scope">{{ courseScope(scope.row.courseScope) }}</template></el-table-column>
        <el-table-column label="剩余次数" width="100" align="center"><template #default="scope">{{ scope.row.cardType === 1 ? scope.row.remainingCount : '-' }}</template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="scope">{{ cardStatus(scope.row.status) }}</template></el-table-column>
      </el-table>
      <template #footer><el-button @click="openView = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="Member">
import { getMember, listMember } from '@/api/member/member'

const { proxy } = getCurrentInstance()
const memberList = ref([])
const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const openView = ref(false)
const detail = ref({})
const queryParams = reactive({ pageNum: 1, pageSize: 10, nickname: undefined, phone: undefined, status: undefined })

function memberStatus(status) { return Number(status) === 1 ? '正常' : '停用' }
function cardType(type) { return Number(type) === 1 ? '次数卡' : '期限卡' }
function courseScope(value) { return ({ 1: '团课', 2: '精品课', 3: '特色课', 4: '私教课' })[Number(value)] || '-' }
function cardStatus(status) { return ['未激活', '已激活', '停用'][Number(status)] || '-' }
function getList() {
  loading.value = true
  listMember(queryParams).then(res => { memberList.value = res.rows; total.value = res.total }).finally(() => { loading.value = false })
}
function handleQuery() { queryParams.pageNum = 1; getList() }
function resetQuery() { proxy.resetForm('queryRef'); handleQuery() }
function handleView(row) {
  getMember(row.id).then(res => { detail.value = res.data; openView.value = true })
}

getList()
</script>
