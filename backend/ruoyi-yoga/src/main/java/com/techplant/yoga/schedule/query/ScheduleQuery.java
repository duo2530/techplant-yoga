package com.techplant.yoga.schedule.query;

import java.time.LocalDate;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 管理端排课列表查询条件（排课管理详细设计 §2.2.1、§2.3.3）。
 *
 * <p><b>日期区间一律筛 {@code schedule_date}</b>（{@code BR-排课-022}），不要用 {@code start_time} 拼区间。</p>
 */
@ApiModel("排课列表查询条件")
public class ScheduleQuery
{
    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限（§2.1.4） */
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty(value = "门店ID", example = "1856739201475235901")
    @Min(value = 1, message = "门店ID必须大于 0")
    private Long storeId;

    @ApiModelProperty(value = "课程ID", example = "1856739201475235801")
    @Min(value = 1, message = "课程ID必须大于 0")
    private Long courseId;

    @ApiModelProperty(value = "课种快照：1团课 2精品课 3私教课 4特色课", example = "1")
    @Min(value = 1, message = "课程类型取值为 1~4")
    @Max(value = 4, message = "课程类型取值为 1~4")
    private Integer courseType;

    @ApiModelProperty(value = "教练ID", example = "1856739201475237001")
    @Min(value = 1, message = "教练ID必须大于 0")
    private Long coachId;

    @ApiModelProperty(value = "起始日期（含），按 schedule_date 判定", example = "2026-10-01")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @ApiModelProperty(value = "结束日期（含），按 schedule_date 判定", example = "2026-10-07")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @ApiModelProperty(value = "状态：1 待上架、2 已上架、3 已取消；不传不限（「已结束」不作为筛选值）", example = "2")
    @Min(value = 1, message = "排课状态取值为 1~3")
    @Max(value = 3, message = "排课状态取值为 1~3")
    private Integer status;

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

    public Long getCourseId()
    {
        return courseId;
    }

    public void setCourseId(Long courseId)
    {
        this.courseId = courseId;
    }

    public Integer getCourseType()
    {
        return courseType;
    }

    public void setCourseType(Integer courseType)
    {
        this.courseType = courseType;
    }

    public Long getCoachId()
    {
        return coachId;
    }

    public void setCoachId(Long coachId)
    {
        this.coachId = coachId;
    }

    public LocalDate getStartDate()
    {
        return startDate;
    }

    public void setStartDate(LocalDate startDate)
    {
        this.startDate = startDate;
    }

    public LocalDate getEndDate()
    {
        return endDate;
    }

    public void setEndDate(LocalDate endDate)
    {
        this.endDate = endDate;
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
