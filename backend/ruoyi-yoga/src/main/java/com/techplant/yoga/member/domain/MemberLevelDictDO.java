package com.techplant.yoga.member.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 会员等级字典的<b>只读</b>投影（若依字典表 {@code sys_dict_data} 中 {@code dict_type = 'member_level'} 的记录）。 */
@TableName("sys_dict_data")
public class MemberLevelDictDO
{
    /** 字典编码（若依主键；本模块只读，不涉及生成策略） */
    @TableId(value = "dict_code", type = IdType.INPUT)
    private Long dictCode;

    /** 字典标签（等级名称，如「V3 金会员」） */
    private String dictLabel;

    /** 字典键值（等级码，如「3」） */
    private String dictValue;

    /** 字典排序（等级展示顺序，升序） */
    private Integer dictSort;

    /** 字典类型（固定 member_level） */
    private String dictType;

    /** 状态：0正常 1停用（若依口径） */
    private String status;

    public Long getDictCode() { return dictCode; }
    public void setDictCode(Long dictCode) { this.dictCode = dictCode; }
    public String getDictLabel() { return dictLabel; }
    public void setDictLabel(String dictLabel) { this.dictLabel = dictLabel; }
    public String getDictValue() { return dictValue; }
    public void setDictValue(String dictValue) { this.dictValue = dictValue; }
    public Integer getDictSort() { return dictSort; }
    public void setDictSort(Integer dictSort) { this.dictSort = dictSort; }
    public String getDictType() { return dictType; }
    public void setDictType(String dictType) { this.dictType = dictType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
