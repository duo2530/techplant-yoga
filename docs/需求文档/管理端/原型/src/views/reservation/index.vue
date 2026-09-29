<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch">
      <el-form-item label="会员" prop="memberName"><el-input v-model="queryParams.memberName" placeholder="会员昵称" clearable @keyup.enter="handleQuery" /></el-form-item>
      <el-form-item label="手机号" prop="phone"><el-input v-model="queryParams.phone" placeholder="手机号" clearable @keyup.enter="handleQuery" /></el-form-item>
      <el-form-item label="课程" prop="courseName"><el-input v-model="queryParams.courseName" placeholder="课程名称" clearable @keyup.enter="handleQuery" /></el-form-item>
      <el-form-item label="门店" prop="storeName"><el-input v-model="queryParams.storeName" placeholder="门店名称" clearable @keyup.enter="handleQuery" /></el-form-item>
      <el-form-item label="上课日期" prop="date"><el-date-picker v-model="queryParams.date" type="date" value-format="YYYY-MM-DD" placeholder="请选择日期" /></el-form-item>
      <el-form-item label="预约状态" prop="status"><el-select v-model="queryParams.status" clearable placeholder="全部" style="width:130px"><el-option label="已预约" :value="1" /><el-option label="已取消" :value="2" /><el-option label="已签到" :value="3" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-tag type="info">同一会员对同一排班只有一条预约记录，人数可以大于 1</el-tag></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="reservationList">
      <el-table-column label="会员" prop="memberName" width="110" />
      <el-table-column label="手机号" prop="phone" width="135" />
      <el-table-column label="课程" prop="courseName" min-width="150" />
      <el-table-column label="门店" prop="storeName" min-width="150" />
      <el-table-column label="教室名称" prop="classroomName" min-width="130" />
      <el-table-column label="教练" prop="coachName" width="100" />
      <el-table-column label="上课时间" prop="startTime" width="175" />
      <el-table-column label="预约人数" prop="bookingCount" width="90" align="center" />
      <el-table-column label="会员卡" prop="cardName" min-width="150" />
      <el-table-column label="状态" width="100" align="center"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="210" align="center" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button v-if="scope.row.status === 1" link type="success" icon="CircleCheck" @click="handleCheckIn(scope.row)">签到</el-button>
          <el-button v-if="scope.row.status === 1" link type="danger" icon="Close" @click="handleCancel(scope.row)">代取消</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="openView" title="预约详情" width="760px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="会员">{{ detail.memberName }}</el-descriptions-item><el-descriptions-item label="手机号">{{ detail.phone }}</el-descriptions-item>
        <el-descriptions-item label="课程">{{ detail.courseName }}</el-descriptions-item><el-descriptions-item label="教练">{{ detail.coachName }}</el-descriptions-item>
        <el-descriptions-item label="门店">{{ detail.storeName }}</el-descriptions-item><el-descriptions-item label="教室名称">{{ detail.classroomName }}</el-descriptions-item>
        <el-descriptions-item label="排班编号">{{ detail.scheduleId }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ detail.startTime }}</el-descriptions-item><el-descriptions-item label="结束时间">{{ detail.endTime }}</el-descriptions-item>
        <el-descriptions-item label="使用会员卡">{{ detail.cardName }}</el-descriptions-item><el-descriptions-item label="卡片类型">{{ cardTypeLabel(detail.cardType) }}</el-descriptions-item>
        <el-descriptions-item label="预约人数">{{ detail.bookingCount }}</el-descriptions-item><el-descriptions-item label="扣次结果">{{ detail.deductionResult }}</el-descriptions-item>
        <el-descriptions-item label="预约状态">{{ statusLabel(detail.status) }}</el-descriptions-item><el-descriptions-item label="预约时间">{{ detail.bookingTime }}</el-descriptions-item>
        <el-descriptions-item label="签到时间">{{ detail.checkInTime || '-' }}</el-descriptions-item><el-descriptions-item label="取消来源">{{ detail.cancelSource || '-' }}</el-descriptions-item>
        <el-descriptions-item label="取消原因" :span="2">{{ detail.cancelReason || '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button @click="openView=false">关闭</el-button></template>
    </el-dialog>

    <el-dialog v-model="openCancel" title="门店代取消预约" width="480px" append-to-body>
      <el-alert title="门店代取消同样不能突破开课前两小时限制。次数卡会按预约人数立即返还，期限卡不处理次数。" type="warning" :closable="false" class="mb20" />
      <el-form ref="cancelRef" :model="cancelForm" :rules="cancelRules" label-width="90px">
        <el-form-item label="会员">{{ currentRow.memberName }}（{{ currentRow.phone }}）</el-form-item>
        <el-form-item label="预约课程">{{ currentRow.courseName }} · {{ currentRow.bookingCount }} 人</el-form-item>
        <el-form-item label="取消原因" prop="reason"><el-radio-group v-model="cancelForm.reason"><el-radio value="临时有事">临时有事</el-radio><el-radio value="请假">请假</el-radio><el-radio value="身体不适">身体不适</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button type="danger" @click="submitCancel">确认取消</el-button><el-button @click="openCancel=false">返回</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="Reservation">
import { cancelReservation, checkInReservation, getReservation, listReservation } from '@/api/reservation/reservation'

const { proxy }=getCurrentInstance()
const loading=ref(false),showSearch=ref(true),openView=ref(false),openCancel=ref(false),total=ref(0)
const reservationList=ref([]),detail=ref({}),currentRow=ref({})
const queryParams=reactive({pageNum:1,pageSize:10,memberName:undefined,phone:undefined,courseName:undefined,storeName:undefined,date:undefined,status:undefined})
const cancelForm=reactive({reason:''})
const cancelRules={reason:[{required:true,message:'请选择取消原因',trigger:'change'}]}

function statusLabel(value){return {1:'已预约',2:'已取消',3:'已签到'}[Number(value)]||'-'}
function statusType(value){return Number(value)===1?'primary':Number(value)===2?'info':'success'}
function cardTypeLabel(value){return Number(value)===1?'次数卡':'期限卡'}
function getList(){loading.value=true;listReservation(queryParams).then(res=>{reservationList.value=res.rows;total.value=res.total}).finally(()=>{loading.value=false})}
function handleQuery(){queryParams.pageNum=1;getList()}
function resetQuery(){proxy.resetForm('queryRef');handleQuery()}
function handleView(row){getReservation(row.id).then(res=>{detail.value=res.data;openView.value=true})}
function handleCheckIn(row){proxy.$modal.confirm(`确认会员“${row.memberName}”已到店并完成签到吗？`).then(()=>checkInReservation(row.id)).then(()=>{proxy.$modal.msgSuccess('签到成功');getList()}).catch(()=>{})}
function handleCancel(row){currentRow.value=row;cancelForm.reason='';openCancel.value=true;nextTick(()=>proxy.$refs.cancelRef?.clearValidate())}
function submitCancel(){proxy.$refs.cancelRef.validate(valid=>{if(!valid)return;cancelReservation(currentRow.value.id,{reason:cancelForm.reason}).then(()=>{proxy.$modal.msgSuccess('预约已取消');openCancel.value=false;getList()}).catch(error=>proxy.$modal.msgError(error.message))})}

getList()
</script>
