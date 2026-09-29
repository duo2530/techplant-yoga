<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch">
      <el-form-item label="上课日期" prop="date"><el-date-picker v-model="queryParams.date" type="date" value-format="YYYY-MM-DD" placeholder="请选择日期" /></el-form-item>
      <el-form-item label="门店" prop="storeId"><el-select v-model="queryParams.storeId" filterable clearable placeholder="全部门店" style="width:180px"><el-option v-for="item in storeOptions" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="课程" prop="courseId"><el-select v-model="queryParams.courseId" filterable clearable placeholder="全部课程" style="width:180px"><el-option v-for="item in courseOptions" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="教练" prop="coachId"><el-select v-model="queryParams.coachId" filterable clearable placeholder="全部教练" style="width:160px"><el-option v-for="item in coachOptions" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="状态" prop="status"><el-select v-model="queryParams.status" clearable placeholder="全部" style="width:130px"><el-option label="待上课" :value="1" /><el-option label="已取消" :value="2" /><el-option label="已完成" :value="3" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button><el-button icon="Refresh" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5"><el-button type="primary" plain icon="Plus" @click="handleAdd">新增排班</el-button></el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="scheduleList">
      <el-table-column label="课程" prop="courseName" min-width="150" />
      <el-table-column label="门店" prop="storeName" min-width="150" />
      <el-table-column label="教练" prop="coachName" width="110" />
      <el-table-column label="开始时间" prop="startTime" width="175" />
      <el-table-column label="结束时间" prop="endTime" width="175" />
      <el-table-column label="教室名称" prop="classroomName" min-width="150" show-overflow-tooltip />
      <el-table-column label="预约人数" width="110" align="center"><template #default="scope">{{ scope.row.bookingCount }} / {{ scope.row.capacity }}</template></el-table-column>
      <el-table-column label="状态" width="100" align="center"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="210" align="center" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleView(scope.row)">查看</el-button>
          <el-button v-if="scope.row.status === 1" link type="primary" icon="Edit" @click="handleUpdate(scope.row)">编辑</el-button>
          <el-button v-if="scope.row.status === 1" link type="danger" icon="Close" @click="handleCancel(scope.row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />

    <el-dialog v-model="open" :title="title" width="680px" append-to-body>
      <el-form ref="scheduleRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="门店" prop="storeId"><el-select v-model="form.storeId" filterable style="width:100%" @change="handleStoreChange"><el-option v-for="item in storeOptions" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="课程" prop="courseId"><el-select v-model="form.courseId" filterable style="width:100%"><el-option v-for="item in courseOptions" :key="item.id" :label="`${item.name}（${courseTypeLabel(item.type)}）`" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="授课教练" prop="coachId"><el-select v-model="form.coachId" filterable style="width:100%"><el-option v-for="item in coachOptions" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="开始时间" prop="startTime"><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择开始时间" style="width:100%" /></el-form-item>
        <el-form-item label="结束时间" prop="endTime"><el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择结束时间" style="width:100%" /></el-form-item>
        <el-form-item label="总容量" prop="capacity"><el-input-number v-model="form.capacity" :min="1" :max="999" /><span class="form-tip">当前已预约 {{ form.bookingCount || 0 }} 人</span></el-form-item>
        <el-form-item label="教室名称" prop="classroomId"><el-select v-model="form.classroomId" filterable placeholder="请先选择门店" style="width:100%"><el-option v-for="item in classroomOptions" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="submitForm">确定</el-button><el-button @click="open=false">取消</el-button></template>
    </el-dialog>

    <el-dialog v-model="openView" title="排班详情" width="720px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="课程">{{ detail.courseName }}</el-descriptions-item><el-descriptions-item label="课种">{{ courseTypeLabel(detail.courseType) }}</el-descriptions-item>
        <el-descriptions-item label="门店">{{ detail.storeName }}</el-descriptions-item><el-descriptions-item label="教练">{{ detail.coachName }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ detail.startTime }}</el-descriptions-item><el-descriptions-item label="结束时间">{{ detail.endTime }}</el-descriptions-item>
        <el-descriptions-item label="教室名称">{{ detail.classroomName }}</el-descriptions-item><el-descriptions-item label="排班状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
        <el-descriptions-item label="总容量">{{ detail.capacity }}</el-descriptions-item><el-descriptions-item label="已预约人数">{{ detail.bookingCount }}</el-descriptions-item>
      </el-descriptions>
      <template #footer><el-button @click="openView=false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="Schedule">
import { addSchedule, cancelSchedule, getSchedule, listSchedule, updateSchedule } from '@/api/schedule/schedule'
import { listStore } from '@/api/store/store'
import { listCourse } from '@/api/course/course'
import { listCoach } from '@/api/coach/coach'
import { listClassroom } from '@/api/classroom/classroom'

const { proxy } = getCurrentInstance()
const loading=ref(false), showSearch=ref(true), open=ref(false), openView=ref(false)
const scheduleList=ref([]), storeOptions=ref([]), courseOptions=ref([]), coachOptions=ref([]), classroomOptions=ref([]), total=ref(0), title=ref(''), detail=ref({})
const queryParams=reactive({ pageNum:1, pageSize:10, date:undefined, storeId:undefined, courseId:undefined, coachId:undefined, status:undefined })
const emptyForm=()=>({ id:undefined, storeId:undefined, courseId:undefined, coachId:undefined, classroomId:undefined, startTime:undefined, endTime:undefined, capacity:12, bookingCount:0 })
const form=ref(emptyForm())
const rules={ storeId:[{required:true,message:'请选择门店',trigger:'change'}], courseId:[{required:true,message:'请选择课程',trigger:'change'}], coachId:[{required:true,message:'请选择教练',trigger:'change'}], classroomId:[{required:true,message:'请选择教室',trigger:'change'}], startTime:[{required:true,message:'请选择开始时间',trigger:'change'}], endTime:[{required:true,message:'请选择结束时间',trigger:'change'}], capacity:[{required:true,message:'请输入总容量',trigger:'change'}] }

function statusLabel(value){ return {1:'待上课',2:'已取消',3:'已完成'}[Number(value)] || '-' }
function statusType(value){ return Number(value)===1?'success':Number(value)===2?'danger':'info' }
function courseTypeLabel(value){ return {1:'团课',2:'精品课',3:'私教课',4:'特色课'}[Number(value)] || '-' }
function getList(){ loading.value=true; listSchedule(queryParams).then(res=>{scheduleList.value=res.rows;total.value=res.total}).finally(()=>{loading.value=false}) }
function loadOptions(){ Promise.all([listStore({pageNum:1,pageSize:100,status:1}),listCourse({pageNum:1,pageSize:100,status:1}),listCoach({pageNum:1,pageSize:100,status:1})]).then(([stores,courses,coaches])=>{storeOptions.value=stores.rows;courseOptions.value=courses.rows;coachOptions.value=coaches.rows}) }
function loadClassrooms(storeId){ classroomOptions.value=[];form.value.classroomId=undefined;if(!storeId)return;listClassroom({pageNum:1,pageSize:100,storeId,status:1}).then(res=>{classroomOptions.value=res.rows}) }
function handleStoreChange(storeId){loadClassrooms(storeId)}
function handleQuery(){queryParams.pageNum=1;getList()}
function resetQuery(){proxy.resetForm('queryRef');handleQuery()}
function handleAdd(){form.value=emptyForm();classroomOptions.value=[];title.value='新增排班';open.value=true;nextTick(()=>proxy.$refs.scheduleRef?.clearValidate())}
function handleUpdate(row){getSchedule(row.id).then(res=>{form.value={...res.data};title.value='修改排班';open.value=true;listClassroom({pageNum:1,pageSize:100,storeId:res.data.storeId,status:1}).then(r=>{classroomOptions.value=r.rows})})}
function handleView(row){getSchedule(row.id).then(res=>{detail.value=res.data;openView.value=true})}
function submitForm(){proxy.$refs.scheduleRef.validate(valid=>{if(!valid)return;const payload={storeId:form.value.storeId,courseId:form.value.courseId,coachId:form.value.coachId,classroomId:form.value.classroomId,startTime:form.value.startTime,endTime:form.value.endTime,capacity:form.value.capacity};const action=form.value.id?updateSchedule(form.value.id,payload):addSchedule(payload);action.then(()=>{proxy.$modal.msgSuccess(form.value.id?'修改成功':'新增成功');open.value=false;getList()}).catch(error=>proxy.$modal.msgError(error.message))})}
function handleCancel(row){proxy.$modal.confirm(`取消排班“${row.courseName}”后，已有预约将自动取消并立即返还次数，确认继续吗？`).then(()=>cancelSchedule(row.id)).then(res=>{proxy.$modal.msgSuccess(res.msg);getList()}).catch(()=>{})}

loadOptions();getList()
</script>

<style scoped>.form-tip{margin-left:12px;color:var(--el-text-color-secondary)}</style>
