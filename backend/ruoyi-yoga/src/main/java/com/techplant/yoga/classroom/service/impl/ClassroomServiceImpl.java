package com.techplant.yoga.classroom.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.classroom.convert.ClassroomConverter;
import com.techplant.yoga.classroom.dao.ClassroomDao;
import com.techplant.yoga.classroom.domain.ClassroomDO;
import com.techplant.yoga.classroom.dto.ClassroomCreateDTO;
import com.techplant.yoga.classroom.dto.ClassroomUpdateDTO;
import com.techplant.yoga.classroom.query.ClassroomQuery;
import com.techplant.yoga.classroom.service.ClassroomService;
import com.techplant.yoga.classroom.vo.ClassroomListItemVO;
import com.techplant.yoga.classroom.vo.ClassroomSummaryVO;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.common.util.CurrentUserUtils;
import com.techplant.yoga.schedule.service.ScheduleService;
import com.techplant.yoga.store.service.StoreService;
import com.techplant.yoga.store.vo.StoreSummaryVO;

/**
 * 教室业务实现（教室管理详细设计 §3.2、§4）。
 *
 * <p><b>物理删除：</b>本表没有 {@code deleted} 字段；<b>改名不可换门店</b>：更新走
 * {@code ClassroomDao#updateName}，显式只 set name，绝不把 {@code store_id} 写空（§3.4 第 2 条）。</p>
 *
 * <p><b>删除门禁：</b>调 {@link ScheduleService#countActiveByClassroomId(Long)} 统计「未结束且待上架或已上架」
 * 的排课数；统计调用失败一律 <b>fail-closed</b>（抛 409「引用检查未完成，已拒绝本次删除」）。</p>
 *
 * <p><b>跨模块注入用 {@code @Lazy}：</b>教室依赖门店／排课，门店与排课又依赖教室读取，
 * 构造器注入会成环，本工程统一在构造器参数上加 {@code @Lazy} 打破。</p>
 */
@Service
public class ClassroomServiceImpl implements ClassroomService
{
    /** 资源不存在 */
    private static final int NOT_FOUND = 404;

    /** 状态冲突：重名／归属不符／删除门禁未通过 */
    private static final int CONFLICT = 409;

    /** 新增未生效 */
    private static final int INTERNAL_ERROR = 500;

    private final ClassroomDao classroomDao;

    private final StoreService storeService;

    private final ScheduleService scheduleService;

    public ClassroomServiceImpl(ClassroomDao classroomDao, @Lazy StoreService storeService,
            @Lazy ScheduleService scheduleService)
    {
        this.classroomDao = classroomDao;
        this.storeService = storeService;
        this.scheduleService = scheduleService;
    }

    /**
     * 查询教室列表（§4.1）：分页查询后按门店批量补齐 storeName（不跨模块读表）
     */
    @Override
    public PageResult<ClassroomListItemVO> page(ClassroomQuery query)
    {
        IPage<ClassroomDO> page = classroomDao.selectPage(query);
        List<ClassroomDO> records = page.getRecords();
        Map<Long, String> storeNames = loadStoreNames(records);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                ClassroomConverter.toListItemVOList(records, storeNames));
    }

    /**
     * 新增教室（§4.2）：门店必须存在；同一门店内名称唯一；<b>不返回新 ID</b>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(ClassroomCreateDTO dto)
    {
        validateStoreExists(dto.getStoreId());

        ClassroomDO classroom = ClassroomConverter.toDO(dto);
        if (classroomDao.selectByStoreIdAndName(classroom.getStoreId(), classroom.getName()) != null)
        {
            throw new ServiceException("该门店下已存在同名教室", CONFLICT);
        }

        int rows = classroomDao.insert(classroom);
        if (rows != 1)
        {
            throw new ServiceException("新增教室失败", INTERNAL_ERROR);
        }
    }

    /**
     * 修改教室（§4.3）：只改名称；同名查重排除自身；<b>改名不加排课门禁</b>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long classroomId, ClassroomUpdateDTO dto)
    {
        ClassroomDO classroom = classroomDao.selectById(classroomId);
        if (classroom == null)
        {
            throw notFound();
        }

        String name = dto.getName() == null ? null : dto.getName().trim();
        if (classroomDao.selectByStoreIdAndNameExcludeId(classroom.getStoreId(), name, classroomId) != null)
        {
            throw new ServiceException("该门店下已存在同名教室", CONFLICT);
        }

        // 显式只更新 name：store_id 保持不变（DTO 里根本没有 storeId 字段）
        classroomDao.updateName(classroomId, name, CurrentUserUtils.getUserIdOrNull());
    }

    /**
     * 删除教室（§4.4）：物理删除，删除前做排课引用门禁（fail-closed）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long classroomId)
    {
        ClassroomDO classroom = classroomDao.selectById(classroomId);
        if (classroom == null)
        {
            throw notFound();
        }

        long activeSchedules = countActiveSchedules(classroomId);
        if (activeSchedules > 0)
        {
            throw new ServiceException(
                    String.format("该教室仍有 %d 节未结束的排课，无法删除", activeSchedules), CONFLICT);
        }

        classroomDao.deleteById(classroomId);
    }

    /**
     * 统计某门店名下的教室数量（门店模块删除门禁用）
     */
    @Override
    public long countByStoreId(Long storeId)
    {
        if (storeId == null)
        {
            return 0L;
        }
        return classroomDao.countByStoreId(storeId);
    }

    /**
     * 校验教室存在且归属指定门店（排课模块用，签名冻结）
     *
     * @throws ServiceException 教室不存在（404）／归属不符（409）
     */
    @Override
    public void validateExistsAndBelongsToStore(Long classroomId, Long storeId)
    {
        ClassroomDO classroom = classroomDao.selectById(classroomId);
        if (classroom == null)
        {
            throw notFound();
        }
        if (storeId == null || !storeId.equals(classroom.getStoreId()))
        {
            throw new ServiceException("所选教室不属于该门店", CONFLICT);
        }
    }

    /**
     * 批量取教室摘要（跨模块出参）：按编号去重后一次查回，避免 N+1
     */
    @Override
    public List<ClassroomSummaryVO> summaries(Collection<Long> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return new ArrayList<ClassroomSummaryVO>();
        }
        LinkedHashSet<Long> distinctIds = new LinkedHashSet<Long>();
        for (Long id : ids)
        {
            if (id != null)
            {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty())
        {
            return new ArrayList<ClassroomSummaryVO>();
        }
        return ClassroomConverter.toSummaryVOList(classroomDao.selectByIds(distinctIds));
    }

    /**
     * 校验门店存在：走 StoreService（跨模块不读表）；门店不存在由对方抛 404「门店不存在或已被删除」
     */
    private void validateStoreExists(Long storeId)
    {
        if (storeService.getById(storeId) == null)
        {
            throw new ServiceException("门店不存在或已被删除", NOT_FOUND);
        }
    }

    /**
     * 按门店编号批量取门店名称（不跨模块读表；门店已被删除时名称为 null）
     */
    private Map<Long, String> loadStoreNames(List<ClassroomDO> classrooms)
    {
        Map<Long, String> storeNames = new HashMap<Long, String>();
        if (classrooms == null || classrooms.isEmpty())
        {
            return storeNames;
        }
        LinkedHashSet<Long> storeIds = new LinkedHashSet<Long>();
        for (ClassroomDO classroom : classrooms)
        {
            if (classroom.getStoreId() != null)
            {
                storeIds.add(classroom.getStoreId());
            }
        }
        if (storeIds.isEmpty())
        {
            return storeNames;
        }
        List<StoreSummaryVO> summaries = storeService.summaries(storeIds);
        if (summaries != null)
        {
            for (StoreSummaryVO summary : summaries)
            {
                storeNames.put(summary.getId(), summary.getName());
            }
        }
        return storeNames;
    }

    /**
     * 统计该教室下「未结束且状态为待上架或已上架」的排课数量（跨模块调用）
     *
     * <p><b>fail-closed：</b>统计调用失败不吞异常、不降级放行。</p>
     */
    private long countActiveSchedules(Long classroomId)
    {
        try
        {
            return scheduleService.countActiveByClassroomId(classroomId);
        }
        catch (RuntimeException e)
        {
            throw new ServiceException("引用检查未完成，已拒绝本次删除", CONFLICT);
        }
    }

    private ServiceException notFound()
    {
        return new ServiceException("教室不存在或已被删除", NOT_FOUND);
    }
}
