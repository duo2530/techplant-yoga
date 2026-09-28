package com.techplant.yoga.coach.query;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/** 教练列表查询条件（详细设计 §2.2.1、§2.2.7）。 */
@ApiModel("教练列表查询条件")
public class CoachQuery
{
    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty("教练名称，后台列表支持模糊匹配")
    @Size(max = 64, message = "教练名称长度不能超过 64")
    private String name;

    @ApiModelProperty(value = "教练状态：1 启用、0 停用；后台列表不传则不限", example = "1")
    @Min(value = 0, message = "教练状态取值为 0 或 1")
    @Max(value = 1, message = "教练状态取值为 0 或 1")
    private Integer status;

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
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
