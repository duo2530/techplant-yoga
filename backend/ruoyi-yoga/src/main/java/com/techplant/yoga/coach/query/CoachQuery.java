package com.techplant.yoga.coach.query;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 教练列表查询条件（教练管理详细设计 §2.2.1、§2.3.3）。
 *
 * <p>教练<b>没有状态</b>，因此查询条件只有姓名与分页；列表固定按 {@code id DESC} 稳定排序。</p>
 */
@ApiModel("教练列表查询条件")
public class CoachQuery
{
    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty("姓名，模糊匹配")
    @Size(max = 32, message = "教练姓名长度不能超过 32")
    private String name;

    @ApiModelProperty(value = "页码，从 1 开始，默认 1", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum;

    @ApiModelProperty(value = "每页条数，默认 10，最大 100", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过 100")
    private Integer pageSize;

    public int pageNumOrDefault() { return pageNum == null ? DEFAULT_PAGE_NUM : pageNum; }
    public int pageSizeOrDefault() { return pageSize == null ? DEFAULT_PAGE_SIZE : pageSize; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
