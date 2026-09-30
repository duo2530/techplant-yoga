package com.techplant.yoga.course.query;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 课程列表查询条件（详细设计 §2.3.5）。
 *
 * <p>由 controller 接收 query string 后装配，交给 service 构造查询条件。</p>
 */
@ApiModel("课程列表查询条件")
public class CourseQuery
{
    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限（§2.1.4） */
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty("课程名称，模糊匹配")
    @Size(max = 64, message = "课程名称长度不能超过 64")
    private String name;

    @ApiModelProperty(value = "门店ID", example = "1856739201475235901")
    @Min(value = 1, message = "门店ID必须大于 0")
    private Long storeId;

    @ApiModelProperty(value = "课程类型，1~4", example = "1")
    @Min(value = 1, message = "课程类型取值为 1~4")
    @Max(value = 4, message = "课程类型取值为 1~4")
    private Integer type;

    @ApiModelProperty(value = "课程状态：1 启用、0 停用；不传不限", example = "1")
    @Min(value = 0, message = "课程状态取值为 0（停用）或 1（启用）")
    @Max(value = 1, message = "课程状态取值为 0（停用）或 1（启用）")
    private Integer status;

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

    public String getName()
    {
        return name;
    }

    public Long getStoreId()
    {
        return storeId;
    }

    public void setStoreId(Long storeId)
    {
        this.storeId = storeId;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public Integer getType()
    {
        return type;
    }

    public void setType(Integer type)
    {
        this.type = type;
    }

    public Integer getStatus()
    {
        return status;
    }

    public void setStatus(Integer status)
    {
        this.status = status;
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
