package com.techplant.yoga.course.query;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import com.techplant.yoga.course.enums.CourseTypeValid;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 课程列表查询条件（课程管理详细设计 §2.3.3、§2.2.1）。
 *
 * <p>筛选：{@code name}（模糊）、{@code courseType}（1~4，不传不限）；分页上限 100。
 * <b>没有门店、没有状态</b> —— 课程是平台级、且无状态。</p>
 */
@ApiModel("课程列表查询条件")
public class CourseQuery
{
    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限 */
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty("课程名称，模糊匹配")
    @Size(max = 64, message = "课程名称长度不能超过 64")
    private String name;

    @ApiModelProperty(value = "课种：1团课 2精品课 3私教课 4特色课；不传不限", example = "1")
    @CourseTypeValid
    private Integer courseType;

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

    public void setName(String name)
    {
        this.name = name;
    }

    public Integer getCourseType()
    {
        return courseType;
    }

    public void setCourseType(Integer courseType)
    {
        this.courseType = courseType;
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
