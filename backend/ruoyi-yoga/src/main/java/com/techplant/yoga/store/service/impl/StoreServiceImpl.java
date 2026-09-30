package com.techplant.yoga.store.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
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
        int rows = storeDao.insert(store);
        if (rows != 1 || store.getId() == null)
        {
            throw new ServiceException("新增门店失败", INTERNAL_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StoreDetailVO update(Long storeId, StoreUpdateDTO dto)
    {
        long start = System.currentTimeMillis();
        StoreDO store = storeDao.selectById(storeId);
        if (store == null)
        {
            throw notFound();
        }

        StoreConverter.applyUpdate(store, dto);
        store.setUpdateBy(CurrentUserUtils.getUserIdOrNull());
        storeDao.updateById(store);

        StoreDO latest = storeDao.selectById(storeId);
        if (latest == null)
        {
            throw notFound();
        }
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
            throw notFound();
        }

        Integer oldStatus = store.getStatus();
        // 本期没有已确认的排班、预约引用门禁规则，状态变更只保存门店自身状态。
        storeDao.updateStatus(storeId, status, CurrentUserUtils.getUserIdOrNull());
    }

    private ServiceException notFound()
    {
        return new ServiceException("门店不存在或已被删除", NOT_FOUND);
    }
}
