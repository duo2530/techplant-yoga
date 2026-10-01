package com.techplant.yoga.schedule.dto;

import io.swagger.annotations.ApiModel;

/**
 * 修改排课的请求体（排课管理详细设计 §2.3.2）：字段与新增**完全相同**（全量编辑）。
 *
 * <p>同样不含 {@code status}、{@code bookedPersons}、{@code courseType}、{@code scheduleDate}：
 * 状态走独立的状态流转接口；课种快照只在「更换课程」时刷新；上课日期由开始时间推导。</p>
 */
@ApiModel("修改排课请求")
public class ScheduleUpdateDTO extends ScheduleCreateDTO
{
}
