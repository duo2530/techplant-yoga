package com.techplant.yoga.coach.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.coach.convert.CoachConverter;
import com.techplant.yoga.coach.dao.CoachDao;
import com.techplant.yoga.coach.domain.CoachDO;
import com.techplant.yoga.coach.dto.CoachCreateDTO;
import com.techplant.yoga.coach.dto.CoachUpdateDTO;
import com.techplant.yoga.coach.query.CoachQuery;
import com.techplant.yoga.coach.service.CoachService;
import com.techplant.yoga.coach.vo.CoachCardVO;
import com.techplant.yoga.coach.vo.CoachDetailVO;
import com.techplant.yoga.coach.vo.CoachIdVO;
import com.techplant.yoga.coach.vo.CoachListItemVO;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.common.util.CurrentUserUtils;

/** 教练业务实现（详细设计 §3.1、§4）。 */
@Service
public class CoachServiceImpl implements CoachService
{
    private static final int STATUS_ENABLED = 1;
    private static final int NOT_FOUND = 404;
    private static final int INTERNAL_ERROR = 500;
    private static final int MAX_ALBUM_SIZE = 5;

    private final CoachDao coachDao;

    public CoachServiceImpl(CoachDao coachDao)
    {
        this.coachDao = coachDao;
    }

    @Override
    public PageResult<CoachListItemVO> page(CoachQuery query)
    {
        IPage<CoachDO> page = coachDao.selectAdminPage(query);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                CoachConverter.toListItemVOList(page.getRecords()));
    }

    @Override
    public PageResult<CoachCardVO> publicPage(CoachQuery query)
    {
        IPage<CoachDO> page = coachDao.selectPublicPage(query);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                CoachConverter.toCardVOList(page.getRecords()));
    }

    @Override
    public List<CoachCardVO> featured()
    {
        return CoachConverter.toCardVOList(coachDao.selectFeatured());
    }

    @Override
    public CoachDetailVO getById(Long coachId)
    {
        CoachDO coach = coachDao.selectById(coachId);
        if (coach == null)
        {
            throw notFound();
        }
        return CoachConverter.toDetailVO(coach);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CoachIdVO create(CoachCreateDTO dto)
    {
        validateAlbumUrls(dto.getAlbumUrls());
        long start = System.currentTimeMillis();

        CoachDO coach = CoachConverter.toDO(dto);
        int rows = coachDao.insert(coach);
        if (rows != 1 || coach.getId() == null)
        {
            throw new ServiceException("新增教练失败", INTERNAL_ERROR);
        }
        return CoachConverter.toIdVO(coach.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CoachDetailVO update(Long coachId, CoachUpdateDTO dto)
    {
        validateAlbumUrls(dto.getAlbumUrls());
        long start = System.currentTimeMillis();
        CoachDO coach = coachDao.selectById(coachId);
        if (coach == null)
        {
            throw notFound();
        }
        CoachConverter.applyUpdate(coach, dto);
        coach.setUpdateBy(CurrentUserUtils.getUserIdOrNull());
        coachDao.updateById(coach);

        CoachDO latest = coachDao.selectById(coachId);
        if (latest == null)
        {
            throw notFound();
        }
        return CoachConverter.toDetailVO(latest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long coachId, Integer status)
    {
        CoachDO coach = coachDao.selectById(coachId);
        if (coach == null)
        {
            throw notFound();
        }
        Integer oldStatus = coach.getStatus();
        coachDao.updateStatus(coachId, status, CurrentUserUtils.getUserIdOrNull());
    }

    private void validateAlbumUrls(List<String> albumUrls)
    {
        if (albumUrls == null)
        {
            return;
        }
        if (albumUrls.size() > MAX_ALBUM_SIZE)
        {
            throw new ServiceException("教练相册最多只能有 5 张图片", INTERNAL_ERROR);
        }
        for (String url : albumUrls)
        {
            if (!hasText(url) || url.length() > 255)
            {
                throw new ServiceException("教练相册图片 URL 不合法", INTERNAL_ERROR);
            }
        }
    }

    private ServiceException notFound()
    {
        return new ServiceException("教练不存在或已被删除", NOT_FOUND);
    }

    private boolean hasText(String value)
    {
        return value != null && !value.trim().isEmpty();
    }
}
