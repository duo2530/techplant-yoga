package com.techplant.yoga.coach.dao;

import java.util.Collection;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.coach.domain.CoachDO;
import com.techplant.yoga.coach.query.CoachQuery;

/** 教练数据访问（教练管理详细设计 §3.2）。 */
public interface CoachDao
{
    /** 管理端列表分页：只 select id/name/avatar_url/phone/审计时间，按 id DESC */
    IPage<CoachDO> selectAdminPage(CoachQuery query);

    /** 按主键查询（全字段，含 gallery JSON 字符串）；不存在返回 {@code null} */
    CoachDO selectById(Long id);

    /** 按主键集合批量查询摘要字段（id/name/avatar_url/intro），供跨模块补齐名称头像 */
    List<CoachDO> selectSummaryByIds(Collection<Long> ids);

    int insert(CoachDO coach);

    /** 全量更新业务字段（显式指定列，不含审计创建列与主键） */
    int updateById(CoachDO coach);

    /** 物理删除（本表无 deleted 字段、无 @TableLogic） */
    int deleteById(Long id);
}
