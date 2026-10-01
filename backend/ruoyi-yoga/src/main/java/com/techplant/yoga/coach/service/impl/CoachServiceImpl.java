package com.techplant.yoga.coach.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.coach.convert.CoachConverter;
import com.techplant.yoga.coach.dao.CoachDao;
import com.techplant.yoga.coach.domain.CoachDO;
import com.techplant.yoga.coach.dto.CoachCreateDTO;
import com.techplant.yoga.coach.dto.CoachUpdateDTO;
import com.techplant.yoga.coach.query.CoachQuery;
import com.techplant.yoga.coach.service.CoachService;
import com.techplant.yoga.coach.vo.CoachDetailVO;
import com.techplant.yoga.coach.vo.CoachListItemVO;
import com.techplant.yoga.coach.vo.CoachSummaryVO;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.common.util.CurrentUserUtils;
import com.techplant.yoga.schedule.service.ScheduleService;

/**
 * 教练业务实现（教练管理详细设计 §3.2、§4）。
 *
 * <p><b>相册的 JSON 转换只在本类发生</b>：DO 里 {@code gallery} 是 JSON 字符串，DTO／VO 里是
 * {@code List<String>}（§3.4 第 2 条）。</p>
 *
 * <p><b>删除门禁</b>走排课模块的 {@link ScheduleService}（不跨模块读表）；统计失败
 * <b>fail-closed</b>，直接拒绝删除（§2.2.5、{@code backend/AGENTS.md} §8.2）。</p>
 */
@Service
public class CoachServiceImpl implements CoachService
{
    private static final int NOT_FOUND = 404;
    private static final int CONFLICT = 409;
    private static final int INTERNAL_ERROR = 500;

    /** 相册元素上限（详细设计 §1.2.3 第 2 条、BR-教练-004） */
    private static final int MAX_GALLERY_SIZE = 5;

    private final CoachDao coachDao;

    /** 跨模块依赖：构造器注入环由 @Lazy 解开（一期四个主数据模块互相依赖排课模块） */
    private final ScheduleService scheduleService;

    public CoachServiceImpl(CoachDao coachDao, @Lazy ScheduleService scheduleService)
    {
        this.coachDao = coachDao;
        this.scheduleService = scheduleService;
    }

    @Override
    public PageResult<CoachListItemVO> page(CoachQuery query)
    {
        IPage<CoachDO> page = coachDao.selectAdminPage(query);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                CoachConverter.toListItemVOList(page.getRecords()));
    }

    @Override
    public CoachDetailVO getById(Long coachId)
    {
        CoachDO coach = coachId == null ? null : coachDao.selectById(coachId);
        if (coach == null)
        {
            throw notFound();
        }
        return CoachConverter.toDetailVO(coach, parseGallery(coach.getGallery()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(CoachCreateDTO dto)
    {
        validateGallery(dto.getGallery());
        CoachDO coach = CoachConverter.toDO(dto, toGalleryJson(dto.getGallery()));
        int rows = coachDao.insert(coach);
        if (rows != 1 || coach.getId() == null)
        {
            throw new ServiceException("新增教练失败", INTERNAL_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CoachDetailVO update(Long coachId, CoachUpdateDTO dto)
    {
        CoachDO coach = coachId == null ? null : coachDao.selectById(coachId);
        if (coach == null)
        {
            throw notFound();
        }
        validateGallery(dto.getGallery());
        CoachConverter.applyUpdate(coach, dto, toGalleryJson(dto.getGallery()));
        coach.setUpdateBy(CurrentUserUtils.getUserIdOrNull());
        coachDao.updateById(coach);

        CoachDO latest = coachDao.selectById(coachId);
        if (latest == null)
        {
            throw notFound();
        }
        return CoachConverter.toDetailVO(latest, parseGallery(latest.getGallery()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long coachId)
    {
        CoachDO coach = coachId == null ? null : coachDao.selectById(coachId);
        if (coach == null)
        {
            throw notFound();
        }
        long activeSchedules = countActiveSchedules(coachId);
        if (activeSchedules > 0)
        {
            throw new ServiceException("该教练仍有 " + activeSchedules + " 节未结束的排课，无法删除", CONFLICT);
        }
        coachDao.deleteById(coachId);
    }

    @Override
    public List<CoachSummaryVO> summaries(Collection<Long> ids)
    {
        Set<Long> distinctIds = new LinkedHashSet<Long>();
        if (ids != null)
        {
            for (Long id : ids)
            {
                if (id != null)
                {
                    distinctIds.add(id);
                }
            }
        }
        if (distinctIds.isEmpty())
        {
            return new ArrayList<CoachSummaryVO>();
        }
        return CoachConverter.toSummaryVOList(coachDao.selectSummaryByIds(distinctIds));
    }

    @Override
    public CoachSummaryVO getSummary(Long coachId)
    {
        CoachDO coach = coachId == null ? null : coachDao.selectById(coachId);
        if (coach == null)
        {
            throw notFound();
        }
        return CoachConverter.toSummaryVO(coach);
    }

    /**
     * 删除门禁统计：调排课模块统计「未结束且状态为待上架或已上架」的排课数。
     * 统计失败一律 fail-closed（不降级放行），由本方法直接终止删除。
     */
    private long countActiveSchedules(Long coachId)
    {
        try
        {
            return scheduleService.countActiveByCoachId(coachId);
        }
        catch (Exception e)
        {
            throw new ServiceException("引用检查未完成，已拒绝本次删除", CONFLICT);
        }
    }

    /** 相册上限校验：新增与修改共用同一段逻辑（详细设计 §3.4 第 3 条） */
    private void validateGallery(List<String> gallery)
    {
        if (gallery != null && gallery.size() > MAX_GALLERY_SIZE)
        {
            throw new ServiceException("相册最多 5 张", CONFLICT);
        }
    }

    /** 相册序列化：去空格、丢空串；空相册存 NULL（详细设计 §1.2.3 第 2 条） */
    private String toGalleryJson(List<String> gallery)
    {
        List<String> normalized = normalizeGallery(gallery);
        return normalized.isEmpty() ? null : JSON.toJSONString(normalized);
    }

    /** 相册反序列化：NULL／空串 → 空列表（详情出参用 List，不用 null） */
    private List<String> parseGallery(String galleryJson)
    {
        if (galleryJson == null || galleryJson.trim().isEmpty())
        {
            return new ArrayList<String>();
        }
        List<String> values = JSON.parseArray(galleryJson, String.class);
        return values == null ? new ArrayList<String>() : values;
    }

    private List<String> normalizeGallery(List<String> gallery)
    {
        List<String> normalized = new ArrayList<String>();
        if (gallery == null)
        {
            return normalized;
        }
        for (String url : gallery)
        {
            if (url != null && !url.trim().isEmpty())
            {
                normalized.add(url.trim());
            }
        }
        return normalized;
    }

    private ServiceException notFound()
    {
        return new ServiceException("教练不存在或已被删除", NOT_FOUND);
    }
}
