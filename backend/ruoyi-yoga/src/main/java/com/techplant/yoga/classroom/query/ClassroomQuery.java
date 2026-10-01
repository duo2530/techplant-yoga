package com.techplant.yoga.classroom.query;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 教室列表查询条件（教室管理详细设计 §2.3.3、§2.2.1）。
 *
 * <p>筛选：{@code storeId}（所属门店，排课表单必传）、{@code name}（模糊）；分页上限 100。</p>
 */
@ApiModel("教室列表查询条件")
public class ClassroomQuery
{
    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限 */
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty(value = "所属门店ID", example = "1856739201475235901")
    @Min(value = 1, message = "门店ID必须大于 0")
    private Long storeId;

    @ApiModelProperty("教室名称，模糊匹配，最长 32")
    @Size(max = 32, message = "教室名称长度不能超过 32")
    private String name;

    @ApiModelProperty(value = "页码，从 1 开始，默认 1", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum;

    @ApiModelProperty(value = "每页条数，默认 10，最大 100", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过 100")
    private Integer pageSize;

    /**
     * 页码，未传时取默认值
     */
    public int pageNumOrDefault()
    {
        return pageNum == null ? DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 每页条数，未传时取默认值
     */
    public int pageSizeOrDefault()
    {
        return pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }

    public Long getStoreId()
    {
        return storeId;
    }

    public void setStoreId(Long storeId)
    {
        this.storeId = storeId;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Integer getPageNum()
    {
        return pageNum;
    }

    public void setPageNum(Integer pageNum)
    {
        this.pageNum = pageNum;
    }

    public Integer getPageSize()
    {
        return pageSize;
    }

    public void setPageSize(Integer pageSize)
    {
        this.pageSize = pageSize;
    }
}
