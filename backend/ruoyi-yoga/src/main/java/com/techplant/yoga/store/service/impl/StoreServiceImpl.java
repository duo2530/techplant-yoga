package com.techplant.yoga.store.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.common.log.BusinessLog;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.common.util.CurrentUserUtils;
import com.techplant.yoga.store.convert.StoreConverter;
import com.techplant.yoga.store.dao.StoreDao;
import com.techplant.yoga.store.domain.StoreDO;
import com.techplant.yoga.store.dto.StoreCreateDTO;
import com.techplant.yoga.store.dto.StoreUpdateDTO;
import com.techplant.yoga.store.query.StoreQuery;
import com.techplant.yoga.store.service.StoreService;
import com.techplant.yoga.store.vo.StoreDetailVO;
import com.techplant.yoga.store.vo.StoreListItemVO;
import com.techplant.yoga.store.vo.StorePublicItemVO;

/** 门店管理业务实现（门店管理详细设计 §3、§4）。 */
@Service
public class StoreServiceImpl implements StoreService
{
    private static final int NOT_FOUND = 404;
    private static final int INTERNAL_ERROR = 500;

    private final StoreDao storeDao;

    public StoreServiceImpl(StoreDao storeDao)
    {
        this.storeDao = storeDao;
    }

    @Override
    public PageResult<StoreListItemVO> page(StoreQuery query)
    {
        IPage<StoreDO> page = storeDao.selectPage(query);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                StoreConverter.toListItemVOList(page.getRecords()));
    }

    @Override
    public List<StorePublicItemVO> publicList()
    {
        return StoreConverter.toPublicItemVOList(storeDao.selectEnabledList());
    }

    @Override
    public StoreDetailVO getById(Long storeId)
    {
        StoreDO store = storeDao.selectById(storeId);
        if (store == null)
        {
            throw notFound();
        }
        return StoreConverter.toDetailVO(store);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(StoreCreateDTO dto)
    {
        long start = System.currentTimeMillis();
        StoreDO store = StoreConverter.toDO(dto);
        BusinessLog.start("CREATE", "store:-", StoreConverter.snapshot(store));
        int rows = storeDao.insert(store);
        if (rows != 1 || store.getId() == null)
        {
            throw new ServiceException("新增门店失败", INTERNAL_ERROR);
        }
        BusinessLog.success("CREATE", "store:" + store.getId(), StoreConverter.snapshot(store),
                System.currentTimeMillis() - start);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreDetailVO update(Long storeId, StoreUpdateDTO dto)
    {
        long start = System.currentTimeMillis();
        StoreDO store = storeDao.selectById(storeId);
        if (store == null)
        {
            BusinessLog.warn("UPDATE", "store:" + storeId, "门店不存在或已被删除");
            throw notFound();
        }

        StoreDO before = copyOf(store);
        BusinessLog.start("UPDATE", "store:" + storeId, StoreConverter.snapshot(store));
        StoreConverter.applyUpdate(store, dto);
        store.setUpdateBy(CurrentUserUtils.getUserIdOrNull());
        storeDao.updateById(store);

        StoreDO latest = storeDao.selectById(storeId);
        if (latest == null)
        {
            BusinessLog.warn("UPDATE", "store:" + storeId, "更新后门店已不存在（并发删除）");
            throw notFound();
        }
        BusinessLog.success("UPDATE", "store:" + storeId, StoreConverter.diff(before, latest),
                System.currentTimeMillis() - start);
        return StoreConverter.toDetailVO(latest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long storeId, Integer status)
    {
        long start = System.currentTimeMillis();
        StoreDO store = storeDao.selectById(storeId);
        if (store == null)
        {
            BusinessLog.warn("CHANGE_STATUS", "store:" + storeId, "门店不存在或已被删除");
            throw notFound();
        }

        Integer oldStatus = store.getStatus();
        // 本期没有已确认的排班、预约引用门禁规则，状态变更只保存门店自身状态。
        storeDao.updateStatus(storeId, status, CurrentUserUtils.getUserIdOrNull());
        BusinessLog.success("CHANGE_STATUS", "store:" + storeId,
                String.format("status: %s -> %s", oldStatus, status), System.currentTimeMillis() - start);
    }

    private StoreDO copyOf(StoreDO source)
    {
        StoreDO copy = new StoreDO();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setRegion(source.getRegion());
        copy.setProvinceCode(source.getProvinceCode());
        copy.setCityCode(source.getCityCode());
        copy.setDistrictCode(source.getDistrictCode());
        copy.setAddress(source.getAddress());
        copy.setPhone(source.getPhone());
        copy.setBusinessType(source.getBusinessType());
        copy.setStoreType(source.getStoreType());
        copy.setBusinessHours(source.getBusinessHours());
        copy.setStatus(source.getStatus());
        return copy;
    }

    private ServiceException notFound()
    {
        return new ServiceException("门店不存在或已被删除", NOT_FOUND);
    }
}
