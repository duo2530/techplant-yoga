package com.techplant.yoga.store.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 门店「所在区域」字典的<b>只读</b>投影（若依字典表 {@code sys_dict_data} 中 {@code dict_type = 'store_region'} 的记录）。 */
@TableName("sys_dict_data")
public class StoreRegionDictDO
{
    /** 字典编码（若依主键；本模块只读，不涉及生成策略） */
    @TableId(value = "dict_code", type = IdType.INPUT)
    private Long dictCode;

    /** 字典标签（区名，如「徐汇区」） */
    private String dictLabel;

    /** 字典键值（6 位行政区划代码，如「310104」） */
    private String dictValue;

    /** 字典类型（固定 store_region） */
    private String dictType;

    /** 状态：0正常 1停用（若依口径） */
    private String status;

    public Long getDictCode() { return dictCode; }
    public void setDictCode(Long dictCode) { this.dictCode = dictCode; }
    public String getDictLabel() { return dictLabel; }
    public void setDictLabel(String dictLabel) { this.dictLabel = dictLabel; }
    public String getDictValue() { return dictValue; }
    public void setDictValue(String dictValue) { this.dictValue = dictValue; }
    public String getDictType() { return dictType; }
    public void setDictType(String dictType) { this.dictType = dictType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
