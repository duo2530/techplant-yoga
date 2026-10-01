package com.techplant.yoga.coach.dao;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.coach.domain.CoachDO;
import com.techplant.yoga.coach.mapper.CoachMapper;
import com.techplant.yoga.coach.query.CoachQuery;

/**
 * 教练数据访问实现。
 *
 * <p>本表<b>没有 deleted 字段、没有 @TableLogic</b>，因此主键删除是物理删除；查询也<b>不</b>拼逻辑删除条件。</p>
 */
@Repository
public class CoachDaoImpl implements CoachDao
{
    private final CoachMapper coachMapper;

    public CoachDaoImpl(CoachMapper coachMapper)
    {
        this.coachMapper = coachMapper;
    }

    @Override
    public IPage<CoachDO> selectAdminPage(CoachQuery query)
    {
        // 列表只 select 必要列：intro 与 gallery 是大字段，不在列表返回（详细设计 §4.1）
        LambdaQueryWrapper<CoachDO> wrapper = Wrappers.<CoachDO>lambdaQuery()
                .select(CoachDO::getId, CoachDO::getName, CoachDO::getAvatarUrl, CoachDO::getPhone,
                        CoachDO::getCreateTime, CoachDO::getUpdateTime);
        boolean hasName = query.getName() != null && !query.getName().trim().isEmpty();
        wrapper.like(hasName, CoachDO::getName, query.getName());
        wrapper.orderByDesc(CoachDO::getId);
        return coachMapper.selectPage(new Page<CoachDO>(query.pageNumOrDefault(), query.pageSizeOrDefault()),
                wrapper);
    }

    @Override
    public CoachDO selectById(Long id)
    {
        return coachMapper.selectById(id);
    }

    @Override
    public List<CoachDO> selectSummaryByIds(Collection<Long> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return new ArrayList<CoachDO>();
        }
        LambdaQueryWrapper<CoachDO> wrapper = Wrappers.<CoachDO>lambdaQuery()
                .select(CoachDO::getId, CoachDO::getName, CoachDO::getAvatarUrl, CoachDO::getIntro)
                .in(CoachDO::getId, ids)
                .orderByDesc(CoachDO::getId);
        return coachMapper.selectList(wrapper);
    }

    @Override
    public int insert(CoachDO coach)
    {
        return coachMapper.insert(coach);
    }

    @Override
    public int updateById(CoachDO coach)
    {
        // 显式指定更新列：不把 create_by/create_time/id 带进 UPDATE
        return coachMapper.update(null, Wrappers.<CoachDO>lambdaUpdate()
                .eq(CoachDO::getId, coach.getId())
                .set(CoachDO::getName, coach.getName())
                .set(CoachDO::getAvatarUrl, coach.getAvatarUrl())
                .set(CoachDO::getIntro, coach.getIntro())
                .set(CoachDO::getPhone, coach.getPhone())
                .set(CoachDO::getGallery, coach.getGallery())
                .set(CoachDO::getUpdateBy, coach.getUpdateBy())
                .set(CoachDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int deleteById(Long id)
    {
        return coachMapper.deleteById(id);
    }
}
