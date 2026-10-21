package com.techplant.yoga.schedule.query;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * 教练页排课查询条件。
 *
 * <p>门店与课种必填；分页默认 {@code pageNum=1, pageSize=10}，上限 100。
 * 日期窗口由服务端按业务时区计算，客户端不传日期。</p>
 */
@ApiModel("教练页排课查询条件")
@Getter
@Setter
public class CoachScheduleQuery {
    /** 默认页码 */
    public static final int DEFAULT_PAGE_NUM = 1;

    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页条数上限 */
    public static final int MAX_PAGE_SIZE = 100;

    @ApiModelProperty(value = "当前门店ID", required = true, example = "1856739201475235901")
    @NotNull(message = "门店ID不能为空")
    private Long storeId;

    @ApiModelProperty(value = "课种 Tab：1 团课、2 精品课、3 私教课、4 特色课", required = true, example = "1")
    @NotNull(message = "课程类型不能为空")
    @Min(value = 1, message = "课程类型取值为 1~4")
    @Max(value = 4, message = "课程类型取值为 1~4")
    private Integer courseType;

    @ApiModelProperty(value = "页码，从 1 开始，默认 1", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum;

    @ApiModelProperty(value = "每页条数，默认 10，最大 100", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过 100")
    private Integer pageSize;

    /** 页码，未传时取默认值 */
    public int pageNumOrDefault() {
        return pageNum == null ? DEFAULT_PAGE_NUM : pageNum;
    }

    /** 每页条数，未传时取默认值 */
    public int pageSizeOrDefault() {
        return pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
