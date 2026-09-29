package com.techplant.yoga.schedule.service;

import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.schedule.dto.ScheduleCreateDTO;
import com.techplant.yoga.schedule.dto.ScheduleStatusDTO;
import com.techplant.yoga.schedule.dto.ScheduleUpdateDTO;
import com.techplant.yoga.schedule.query.ScheduleQuery;
import com.techplant.yoga.schedule.vo.ScheduleDetailVO;
import com.techplant.yoga.schedule.vo.ScheduleListItemVO;

/** 排班管理服务。 */
public interface ScheduleService
{
    PageResult<ScheduleListItemVO> page(ScheduleQuery query);
    ScheduleDetailVO getById(Long scheduleId);
    ScheduleDetailVO create(ScheduleCreateDTO dto);
    ScheduleDetailVO update(Long scheduleId, ScheduleUpdateDTO dto);
    void updateStatus(Long scheduleId, ScheduleStatusDTO dto);
}
