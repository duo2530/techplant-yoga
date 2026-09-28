package com.techplant.yoga.coach.dao;

import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.coach.domain.CoachDO;
import com.techplant.yoga.coach.query.CoachQuery;

/** 教练数据访问（详细设计 §3.1）。 */
public interface CoachDao
{
    IPage<CoachDO> selectAdminPage(CoachQuery query);
    IPage<CoachDO> selectPublicPage(CoachQuery query);
    List<CoachDO> selectFeatured();
    CoachDO selectById(Long id);
    int insert(CoachDO coach);
    int updateById(CoachDO coach);
    int updateStatus(Long id, Integer status, Long updateBy);
}
