package com.techplant.yoga.coach.dao;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.coach.domain.CoachDO;
import com.techplant.yoga.coach.mapper.CoachMapper;
import com.techplant.yoga.coach.query.CoachQuery;

/** 教练数据访问实现。 */
@Repository
public class CoachDaoImpl implements CoachDao
{
    private static final int STATUS_ENABLED = 1;
    private static final String GOLD_TITLE = "金牌教练";

    private final CoachMapper coachMapper;

    public CoachDaoImpl(CoachMapper coachMapper)
    {
        this.coachMapper = coachMapper;
    }

    @Override
    public IPage<CoachDO> selectAdminPage(CoachQuery query)
    {
        LambdaQueryWrapper<CoachDO> wrapper = baseListWrapper();
        boolean hasName = query.getName() != null && !query.getName().trim().isEmpty();
        wrapper.like(hasName, CoachDO::getName, query.getName());
        wrapper.eq(query.getStatus() != null, CoachDO::getStatus, query.getStatus());
        wrapper.orderByDesc(CoachDO::getId);
        return coachMapper.selectPage(new Page<CoachDO>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
    }

    @Override
    public IPage<CoachDO> selectPublicPage(CoachQuery query)
    {
        LambdaQueryWrapper<CoachDO> wrapper = baseListWrapper();
        boolean hasName = query.getName() != null && !query.getName().trim().isEmpty();
        wrapper.eq(CoachDO::getStatus, STATUS_ENABLED);
        wrapper.like(hasName, CoachDO::getName, query.getName());
        wrapper.orderByDesc(CoachDO::getId);
        return coachMapper.selectPage(new Page<CoachDO>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
    }

    @Override
    public List<CoachDO> selectFeatured()
    {
        LambdaQueryWrapper<CoachDO> wrapper = baseListWrapper();
        wrapper.eq(CoachDO::getStatus, STATUS_ENABLED)
                .eq(CoachDO::getTitle, GOLD_TITLE)
                .orderByDesc(CoachDO::getId);
        return coachMapper.selectList(wrapper);
    }

    private LambdaQueryWrapper<CoachDO> baseListWrapper()
    {
        return Wrappers.<CoachDO>lambdaQuery().select(CoachDO::getId, CoachDO::getName, CoachDO::getTitle,
                CoachDO::getAvatarUrl, CoachDO::getStatus);
    }

    @Override
    public CoachDO selectById(Long id)
    {
        return coachMapper.selectById(id);
    }

    @Override
    public int insert(CoachDO coach)
    {
        return coachMapper.insert(coach);
    }

    @Override
    public int updateById(CoachDO coach)
    {
        return coachMapper.update(null, Wrappers.<CoachDO>lambdaUpdate()
                .eq(CoachDO::getId, coach.getId())
                .set(CoachDO::getIntro, coach.getIntro())
                .set(CoachDO::getName, coach.getName())
                .set(CoachDO::getTitle, coach.getTitle())
                .set(CoachDO::getAvatarUrl, coach.getAvatarUrl())
                .set(CoachDO::getAlbumUrlsJson, coach.getAlbumUrlsJson())
                .set(CoachDO::getUpdateBy, coach.getUpdateBy())
                .set(CoachDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int updateStatus(Long id, Integer status, Long updateBy)
    {
        return coachMapper.update(null, Wrappers.<CoachDO>lambdaUpdate()
                .eq(CoachDO::getId, id)
                .set(CoachDO::getStatus, status)
                .set(CoachDO::getUpdateBy, updateBy)
                .set(CoachDO::getUpdateTime, LocalDateTime.now()));
    }
}
