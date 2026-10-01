package com.techplant.yoga.classroom.vo;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 教室列表项（教室管理详细设计 §2.3.1）。
 *
 * <p>字段只有「所属门店 ＋ 名称 ＋ 时间」，<b>列表项已含编辑弹窗回填所需的全部字段</b>，
 * 所以本模块<b>没有详情接口</b>（§2.2 说明）。{@code storeName} 由 service 调
 * {@code StoreService} 批量补齐，不跨模块读表。</p>
 */
@ApiModel("教室列表项")
public class ClassroomListItemVO
{
    @ApiModelProperty(value = "教室ID（雪花ID，字符串）", example = "1856739201475236001")
    private Long id;

    @ApiModelProperty(value = "所属门店ID", example = "1856739201475235901")
    private Long storeId;

    @ApiModelProperty(value = "所属门店名称（跨模块补齐）", example = "徐汇店")
    private String storeName;

    @ApiModelProperty(value = "教室名称", example = "瑜伽团课大教室")
    private String name;

    @ApiModelProperty(value = "创建时间", example = "2026-10-01 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间", example = "2026-10-01 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getStoreId()
    {
        return storeId;
    }

    public void setStoreId(Long storeId)
    {
        this.storeId = storeId;
    }

    public String getStoreName()
    {
        return storeName;
    }

    public void setStoreName(String storeName)
    {
        this.storeName = storeName;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public LocalDateTime getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime)
    {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime)
    {
        this.updateTime = updateTime;
    }
}
