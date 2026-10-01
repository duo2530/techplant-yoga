package com.techplant.yoga.schedule.query;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 用户端排课列表查询条件（《用户端接口详细设计》§2.2）。
 *
 * <p>三个参数**全部必填**：{@code storeId}、{@code courseType}、{@code date}。
 * 缺参由这里拦住（{@code 500}，框架既有行为）；日期格式非法、14 天窗口、门店不存在在 service 层判定。</p>
 *
 * <p>{@code date} 刻意用 {@code String} 接收，由 service 解析成 {@code LocalDate}：
 * 这样「日期格式非法」能给出明确的提示语，而不是框架的类型转换异常信息。</p>
 *
 * <p>服务端固定施加的过滤条件（客户端不可覆盖）：{@code status <> 1}、{@code store_id}、
 * {@code course_type}（<b>快照列</b>）、{@code schedule_date}（<b>派生列</b>）。</p>
 */
@ApiModel("用户端排课列表查询条件")
public class PublicScheduleQuery
{
    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限（§1.3 约定，超出由 MP 分页插件钳制到 100） */
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty(value = "当前门店ID", required = true, example = "1856739201475235901")
    @NotNull(message = "门店ID不能为空")
    private Long storeId;

    @ApiModelProperty(value = "课种 Tab：1 团课、2 精品课、3 私教课、4 特色课", required = true, example = "1")
    @NotNull(message = "课程类型不能为空")
    @Min(value = 1, message = "课程类型取值为 1~4")
    @Max(value = 4, message = "课程类型取值为 1~4")
    private Integer courseType;

    @ApiModelProperty(value = "日期条选中的日期 yyyy-MM-dd", required = true, example = "2026-10-03")
    @NotBlank(message = "日期不能为空")
    private String date;

    @ApiModelProperty(value = "页码，从 1 开始，默认 1", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum;

    @ApiModelProperty(value = "每页条数，默认 10，最大 100", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过 100")
    private Integer pageSize;

    /** 页码，未传时取默认值 */
    public int pageNumOrDefault()
    {
        return pageNum == null ? DEFAULT_PAGE_NUM : pageNum;
    }

    /** 每页条数，未传时取默认值 */
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

    public Integer getCourseType()
    {
        return courseType;
    }

    public void setCourseType(Integer courseType)
    {
        this.courseType = courseType;
    }

    public String getDate()
    {
        return date;
    }

    public void setDate(String date)
    {
        this.date = date;
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
