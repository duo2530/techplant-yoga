package com.techplant.yoga.store.service.impl;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.classroom.service.ClassroomService;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.common.util.CurrentUserUtils;
import com.techplant.yoga.schedule.service.ScheduleService;
import com.techplant.yoga.store.convert.StoreConverter;
import com.techplant.yoga.store.dao.StoreDao;
import com.techplant.yoga.store.dao.StoreRegionDictDao;
import com.techplant.yoga.store.domain.StoreDO;
import com.techplant.yoga.store.domain.StoreNoCounterDO;
import com.techplant.yoga.store.dto.StoreCreateDTO;
import com.techplant.yoga.store.dto.StoreUpdateDTO;
import com.techplant.yoga.store.enums.StoreTypeEnum;
import com.techplant.yoga.store.query.StorePublicQuery;
import com.techplant.yoga.store.query.StoreQuery;
import com.techplant.yoga.store.service.StoreService;
import com.techplant.yoga.store.vo.StoreDetailVO;
import com.techplant.yoga.store.vo.StoreListItemVO;
import com.techplant.yoga.store.vo.StorePublicItemVO;
import com.techplant.yoga.store.vo.StoreSummaryVO;

/** 门店业务实现。 */
@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {
    /** 资源不存在 */
    private static final int NOT_FOUND = 404;

    /** 状态冲突：名称重复、编号冲突或已用尽、删除门禁未通过 */
    private static final int CONFLICT = 409;

    /** 参数校验失败 */
    private static final int PARAM_INVALID = 400;

    private static final int INTERNAL_ERROR = 500;

    /** 门店编号上限（从 0001 起连续分配，0000 不使用） */
    private static final int MAX_STORE_NO = 9999;

    /** 门店名称唯一索引名（唯一键冲突归类用） */
    private static final String UK_STORE_NAME = "uk_store_name";

    /** 门店编号唯一索引名（唯一键冲突归类用） */
    private static final String UK_STORE_NO = "uk_store_no";

    private final StoreDao storeDao;

    private final StoreRegionDictDao storeRegionDictDao;

    @Lazy
    private final ClassroomService classroomService;

    @Lazy
    private final ScheduleService scheduleService;

    @Override
    public PageResult<StoreListItemVO> page(StoreQuery query) {
        Map<String, String> regionLabelMap = loadRegionLabelMap();
        validateRegionCode(query.getRegionCode(), regionLabelMap);

        IPage<StoreDO> page = storeDao.selectPage(query);
        // IPage 不出 service 层
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                StoreConverter.toListItemVOList(page.getRecords(), regionLabelMap));
    }

    @Override
    public PageResult<StorePublicItemVO> publicPage(StorePublicQuery query) {
        Map<String, String> regionLabelMap = loadRegionLabelMap();
        validateRegionCode(query.getRegionCode(), regionLabelMap);

        IPage<StoreDO> page = storeDao.selectPublicPage(query);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                StoreConverter.toPublicItemVOList(page.getRecords(), regionLabelMap));
    }

    @Override
    public StoreDetailVO getById(Long storeId) {
        StoreDO store = storeDao.selectById(storeId);
        if (store == null) {
            throw notFound();
        }
        Map<String, String> regionLabelMap = loadRegionLabelMap();
        return StoreConverter.toDetailVO(store, StoreConverter.regionNameOf(regionLabelMap, store.getRegionCode()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(StoreCreateDTO dto) {
        validateStoreType(dto.getStoreType());
        validateRegionCode(dto.getRegionCode(), loadRegionLabelMap());
        checkNameUnique(dto.getName(), null);

        StoreDO store = StoreConverter.toDO(dto);
        store.setStoreNo(allocateStoreNo());
        int rows;
        try {
            rows = storeDao.insert(store);
        } catch (DuplicateKeyException e) {
            // 名称与编号各有唯一键，按违反的索引区分；其他数据库异常不归为编号冲突
            throw conflictOf(e);
        }
        if (rows != 1 || store.getId() == null) {
            throw new ServiceException("新增门店失败", INTERNAL_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreDetailVO update(Long storeId, StoreUpdateDTO dto) {
        StoreDO store = storeDao.selectById(storeId);
        if (store == null) {
            throw notFound();
        }

        validateStoreType(dto.getStoreType());
        Map<String, String> regionLabelMap = loadRegionLabelMap();
        validateRegionCode(dto.getRegionCode(), regionLabelMap);
        checkNameUnique(dto.getName(), storeId);
        // 修改门店不做引用检查：已被排课引用时仍允许修改

        StoreConverter.applyUpdate(store, dto);
        store.setUpdateBy(CurrentUserUtils.getUserIdOrNull());
        try {
            storeDao.updateById(store);
        } catch (DuplicateKeyException e) {
            throw nameDuplicated(e);
        }

        StoreDO latest = storeDao.selectById(storeId);
        if (latest == null) {
            throw notFound();
        }
        return StoreConverter.toDetailVO(latest, StoreConverter.regionNameOf(regionLabelMap, latest.getRegionCode()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long storeId) {
        // ① 门店存在
        StoreDO store = storeDao.selectById(storeId);
        if (store == null) {
            throw notFound();
        }

        // ② 名下没有教室
        long classroomCount = countClassrooms(storeId);
        if (classroomCount > 0) {
            throw new ServiceException(String.format("该门店下仍有 %d 间教室，无法删除", classroomCount), CONFLICT);
        }

        // ③ 没有未结束、且状态为待上架或已上架的排课
        long scheduleCount = countActiveSchedules(storeId);
        if (scheduleCount > 0) {
            throw new ServiceException(String.format("该门店仍有 %d 节未结束的排课，无法删除", scheduleCount), CONFLICT);
        }

        // ④ 全部通过 → 物理删除（已取消／已结束的历史排课不阻塞，删除后其门店显示为空）
        storeDao.deleteById(storeId);
    }

    @Override
    public List<StoreSummaryVO> summaries(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<StoreSummaryVO>();
        }
        Set<Long> distinctIds = new LinkedHashSet<Long>();
        for (Long id : ids) {
            if (id != null) {
                distinctIds.add(id);
            }
        }
        if (distinctIds.isEmpty()) {
            return new ArrayList<StoreSummaryVO>();
        }
        return StoreConverter.toSummaryVOList(storeDao.selectByIds(distinctIds));
    }

    /**
     * 名称唯一校验：新增时 {@code excludeId} 为 null，修改时排除自身
     *
     * <p>重复 → 409「门店名称已存在」。</p>
     */
    private void checkNameUnique(String name, Long excludeId) {
        StoreDO existing = storeDao.selectByName(name);
        if (existing == null) {
            return;
        }
        if (excludeId != null && excludeId.equals(existing.getId())) {
            return;
        }
        throw new ServiceException("门店名称已存在", CONFLICT);
    }

    /**
     * 在本事务内分配下一个门店编号
     *
     * <p>锁定计数器后检查上限再递增，编号与门店记录同事务提交或回滚；
     * 计数器未初始化时拒绝新增，不退回按门店表最大值推算。</p>
     */
    private String allocateStoreNo() {
        StoreNoCounterDO counter = storeDao.selectCounterForUpdate();
        if (counter == null || counter.getLastNo() == null) {
            throw new ServiceException("门店编号计数器未初始化，无法新增门店", INTERNAL_ERROR);
        }
        int nextNo = counter.getLastNo() + 1;
        if (nextNo > MAX_STORE_NO) {
            throw new ServiceException("门店编号已用尽", CONFLICT);
        }
        if (storeDao.updateCounterLastNo(nextNo) != 1) {
            throw new ServiceException("门店编号生成冲突，请稍后重试", CONFLICT);
        }
        return String.format(Locale.ROOT, "%04d", nextNo);
    }

    /** 唯一键冲突归类：门店编号冲突与名称重复各自成对提示，其余数据库异常原样抛出 */
    private RuntimeException conflictOf(DuplicateKeyException cause) {
        String message = cause.getMessage() == null ? "" : cause.getMessage();
        if (message.contains(UK_STORE_NO)) {
            ServiceException exception = new ServiceException("门店编号生成冲突，请稍后重试", CONFLICT);
            exception.initCause(cause);
            return exception;
        }
        if (message.contains(UK_STORE_NAME)) {
            return nameDuplicated(cause);
        }
        return cause;
    }

    /**
     * 统计门店名下教室数（跨模块调用，删除门禁第 ② 步）
     *
     * <p><b>fail-closed</b>：调用失败直接终止删除，不降级放行。</p>
     */
    private long countClassrooms(Long storeId) {
        try {
            return classroomService.countByStoreId(storeId);
        } catch (RuntimeException e) {
            throw referenceCheckFailed(e);
        }
    }

    /**
     * 统计门店下未结束的排课数（跨模块调用，删除门禁第 ③ 步）
     *
     * <p>统计口径由排课模块保证：{@code end_time > NOW AND status IN (1,2)}；
     * 调用失败同样 fail-closed。</p>
     */
    private long countActiveSchedules(Long storeId) {
        try {
            return scheduleService.countActiveByStoreId(storeId);
        } catch (RuntimeException e) {
            throw referenceCheckFailed(e);
        }
    }

    /** 引用统计失败：拒绝本次删除，并把原始异常挂上，便于排查 */
    private ServiceException referenceCheckFailed(RuntimeException cause) {
        ServiceException exception = new ServiceException("引用检查未完成，已拒绝本次删除", CONFLICT);
        exception.initCause(cause);
        return exception;
    }

    /** 门店类型取值校验（1 主力店 / 2 精品店），越界 → 参数校验失败（400） */
    private void validateStoreType(Integer storeType) {
        if (!StoreTypeEnum.isValid(storeType)) {
            throw new ServiceException("门店类型取值为 1 或 2", PARAM_INVALID);
        }
    }

    /** 所在区域取值校验：必须是字典 {@code store_region} 中存在的 code，越界即拒绝 */
    private void validateRegionCode(String regionCode, Map<String, String> regionLabelMap) {
        if (regionCode == null || regionCode.trim().isEmpty()) {
            return;
        }
        if (!regionLabelMap.containsKey(regionCode.trim())) {
            throw new ServiceException("所在区域取值非法", PARAM_INVALID);
        }
    }

    /** 取一次区域字典映射（code → 区名），同一请求内的校验与翻译共用 */
    private Map<String, String> loadRegionLabelMap() {
        return storeRegionDictDao.selectRegionLabelMap();
    }

    private ServiceException notFound() {
        return new ServiceException("门店不存在或已被删除", NOT_FOUND);
    }

    private ServiceException nameDuplicated(DuplicateKeyException cause) {
        ServiceException exception = new ServiceException("门店名称已存在", CONFLICT);
        exception.initCause(cause);
        return exception;
    }
}
