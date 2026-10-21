package com.techplant.yoga.coach.dao;

import java.util.Collection;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.coach.domain.CoachDO;
import com.techplant.yoga.coach.query.CoachQuery;

/** 教练数据访问。 */
public interface CoachDao {
    /** 管理端列表分页：只 select id/name/avatar_url/phone/level/审计时间，按 id DESC */
    IPage<CoachDO> selectAdminPage(CoachQuery query);

    /** 按主键查询（全字段，含 gallery JSON 字符串）；不存在返回 {@code null} */
    CoachDO selectById(Long id);

    /** 按主键集合批量查询摘要字段（id/name/avatar_url/intro），供跨模块补齐名称头像 */
    List<CoachDO> selectSummaryByIds(Collection<Long> ids);

    /** 全部金牌教练（level=2），只取公开字段（id/name/avatar_url/intro），按 id DESC */
    List<CoachDO> selectFeatured();

    /** 公开详情：只取 id/name/avatar_url/intro/gallery；不存在返回 {@code null} */
    CoachDO selectPublicById(Long id);

    int insert(CoachDO coach);

    /**
     * 全量更新业务字段（显式指定列，不含审计创建列、主键与 level）
     *
     * <p>level 只能通过 {@link #updateLevel(Long, Integer, Long)} 修改，资料编辑不得覆盖。</p>
     */
    int updateById(CoachDO coach);

    /** 只更新 level 列与更新审计字段（等级设置的唯一写入口） */
    int updateLevel(Long id, Integer level, Long updateBy);

    /** 物理删除（本表无 deleted 字段、无 @TableLogic） */
    int deleteById(Long id);
}
