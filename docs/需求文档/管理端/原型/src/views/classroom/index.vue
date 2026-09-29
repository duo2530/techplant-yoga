<template>
  <div class="app-container">
    <el-form :model="queryParams" :inline="true" v-show="showSearch">
      <el-form-item label="门店"><el-select v-model="queryParams.storeId" clearable placeholder="全部门店" style="width: 220px"><el-option v-for="item in stores" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="教室名称"><el-input v-model="queryParams.name" clearable placeholder="请输入教室名称" @keyup.enter="getList" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 120px"><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="Search" @click="getList">搜索</el-button><el-button icon="Refresh" @click="reset">重置</el-button></el-form-item>
    </el-form>
    <el-row class="mb8"><el-col :span="1.5"><el-button type="primary" plain icon="Plus" @click="add">新增教室</el-button></el-col><right-toolbar v-model:showSearch="showSearch" @queryTable="getList" /></el-row>
    <el-table v-loading="loading" :data="rows">
      <el-table-column label="门店" prop="storeName" min-width="180" />
      <el-table-column label="教室名称" prop="name" min-width="180" />
      <el-table-column label="容纳人数" prop="capacity" width="120" align="center" />
      <el-table-column label="备注" prop="remark" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="100" align="center"><template #default="s"><el-tag :type="s.row.status===1?'success':'info'">{{ s.row.status===1?'启用':'停用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="100" fixed="right"><template #default="s"><el-button link type="primary" icon="Edit" @click="edit(s.row)">编辑</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total>0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    <el-dialog v-model="open" :title="title" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属门店" prop="storeId"><el-select v-model="form.storeId" filterable style="width:100%"><el-option v-for="item in stores" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="教室名称" prop="name"><el-input v-model="form.name" maxlength="64" placeholder="例如：普拉提教室" /></el-form-item>
        <el-form-item label="容纳人数" prop="capacity"><el-input-number v-model="form.capacity" :min="1" :max="999" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="0">停用</el-radio></el-radio-group></el-form-item>
      </el-form>
      <template #footer><el-button type="primary" @click="submit">保存</el-button><el-button @click="open=false">取消</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup name="Classroom">
import { addClassroom, listClassroom, updateClassroom } from '@/api/classroom/classroom'
import { listStore } from '@/api/store/store'

const { proxy } = getCurrentInstance()
const loading=ref(false), showSearch=ref(true), open=ref(false), title=ref(''), rows=ref([]), stores=ref([]), total=ref(0)
const queryParams=reactive({pageNum:1,pageSize:10,storeId:undefined,name:undefined,status:undefined})
const form=ref({})
const rules={storeId:[{required:true,message:'请选择所属门店',trigger:'change'}],name:[{required:true,message:'请输入教室名称',trigger:'blur'}],capacity:[{required:true,message:'请输入容纳人数',trigger:'change'}]}
function getList(){loading.value=true;listClassroom(queryParams).then(r=>{rows.value=r.rows;total.value=r.total}).finally(()=>loading.value=false)}
function reset(){queryParams.pageNum=1;queryParams.storeId=undefined;queryParams.name=undefined;queryParams.status=undefined;getList()}
function add(){form.value={storeId:undefined,name:'',capacity:12,remark:'',status:1};title.value='新增教室';open.value=true}
function edit(row){form.value={...row};title.value='编辑教室';open.value=true}
function submit(){proxy.$refs.formRef.validate(valid=>{if(!valid)return;const action=form.value.id?updateClassroom(form.value.id,form.value):addClassroom(form.value);action.then(()=>{proxy.$modal.msgSuccess('保存成功');open.value=false;getList()}).catch(e=>proxy.$modal.msgError(e.message))})}
Promise.all([listStore({pageNum:1,pageSize:100}),getList()]).then(([r])=>{stores.value=r.rows})
</script>
