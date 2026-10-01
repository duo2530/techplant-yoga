package com.techplant.yoga.schedule.vo;

import io.swagger.annotations.ApiModel;

/**
 * 管理端排课详情（排课管理详细设计 §2.3.1）。
 *
 * <p>字段与 {@link ScheduleListItemVO} **完全相同**——列表已经带了排课全部业务字段，不再裁剪。</p>
 */
@ApiModel("管理端排课详情")
public class ScheduleDetailVO extends ScheduleListItemVO
{
}
