package com.techplant.yoga.coach.service;

import java.util.List;
import com.techplant.yoga.coach.dto.CoachCreateDTO;
import com.techplant.yoga.coach.dto.CoachUpdateDTO;
import com.techplant.yoga.coach.query.CoachQuery;
import com.techplant.yoga.coach.vo.CoachCardVO;
import com.techplant.yoga.coach.vo.CoachDetailVO;
import com.techplant.yoga.coach.vo.CoachIdVO;
import com.techplant.yoga.coach.vo.CoachListItemVO;
import com.techplant.yoga.common.response.PageResult;

/** 教练业务接口（详细设计 §3.1）。 */
public interface CoachService
{
    PageResult<CoachListItemVO> page(CoachQuery query);
    PageResult<CoachCardVO> publicPage(CoachQuery query);
    List<CoachCardVO> featured();
    CoachDetailVO getById(Long coachId);
    CoachIdVO create(CoachCreateDTO dto);
    CoachDetailVO update(Long coachId, CoachUpdateDTO dto);
    void updateStatus(Long coachId, Integer status);
}
