package com.techplant.yoga.store.dao;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.store.domain.StoreDO;
import com.techplant.yoga.store.mapper.StoreMapper;
import com.techplant.yoga.store.query.StoreQuery;

/** 门店数据访问实现。 */
@Repository
public class StoreDaoImpl implements StoreDao
{
    private static final int STATUS_ENABLED = 1;

    private final StoreMapper storeMapper;

    public StoreDaoImpl(StoreMapper storeMapper)
    {
        this.storeMapper = storeMapper;
    }

    @Override
    public IPage<StoreDO> selectPage(StoreQuery query)
    {
        LambdaQueryWrapper<StoreDO> wrapper = Wrappers.<StoreDO>lambdaQuery();
        wrapper.select(StoreDO::getId, StoreDO::getName, StoreDO::getRegion, StoreDO::getProvinceCode,
                StoreDO::getCityCode, StoreDO::getDistrictCode, StoreDO::getAddress, StoreDO::getPhone,
                StoreDO::getBusinessType, StoreDO::getStoreType,
                StoreDO::getBusinessHours, StoreDO::getStatus, StoreDO::getCreateTime,
                StoreDO::getUpdateTime);
        boolean hasName = query.getName() != null && !query.getName().trim().isEmpty();
        boolean hasRegion = query.getRegion() != null && !query.getRegion().trim().isEmpty();
        wrapper.like(hasName, StoreDO::getName, query.getName());
        wrapper.like(hasRegion, StoreDO::getRegion, query.getRegion());
        wrapper.eq(query.getBusinessType() != null, StoreDO::getBusinessType, query.getBusinessType());
        wrapper.eq(query.getStoreType() != null, StoreDO::getStoreType, query.getStoreType());
        wrapper.eq(query.getStatus() != null, StoreDO::getStatus, query.getStatus());
        // 稳定排序，避免分页时记录重复或遗漏。
        wrapper.orderByAsc(StoreDO::getRegion);
        wrapper.orderByAsc(StoreDO::getName);
        wrapper.orderByDesc(StoreDO::getId);
        return storeMapper.selectPage(new Page<StoreDO>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
    }

    @Override
    public List<StoreDO> selectEnabledList()
    {
        LambdaQueryWrapper<StoreDO> wrapper = Wrappers.<StoreDO>lambdaQuery();
        wrapper.select(StoreDO::getId, StoreDO::getName, StoreDO::getRegion, StoreDO::getProvinceCode,
                StoreDO::getCityCode, StoreDO::getDistrictCode, StoreDO::getAddress, StoreDO::getPhone,
                StoreDO::getBusinessType, StoreDO::getStoreType, StoreDO::getBusinessHours);
        wrapper.eq(StoreDO::getStatus, STATUS_ENABLED);
        wrapper.orderByAsc(StoreDO::getRegion);
        wrapper.orderByAsc(StoreDO::getName);
        wrapper.orderByDesc(StoreDO::getId);
        return storeMapper.selectList(wrapper);
    }

    @Override
    public StoreDO selectById(Long id)
    {
        return storeMapper.selectById(id);
    }

    @Override
    public int insert(StoreDO store)
    {
        return storeMapper.insert(store);
    }

    @Override
    public int updateById(StoreDO store)
    {
        // 显式 SET，保证全量 PUT 能覆盖用户清空后的可选值；当前字段均为必填，保留完整更新语义。
        return storeMapper.update(null, Wrappers.<StoreDO>lambdaUpdate()
                .eq(StoreDO::getId, store.getId())
                .set(StoreDO::getName, store.getName())
                .set(StoreDO::getRegion, store.getRegion())
                .set(StoreDO::getProvinceCode, store.getProvinceCode())
                .set(StoreDO::getCityCode, store.getCityCode())
                .set(StoreDO::getDistrictCode, store.getDistrictCode())
                .set(StoreDO::getAddress, store.getAddress())
                .set(StoreDO::getPhone, store.getPhone())
                .set(StoreDO::getBusinessType, store.getBusinessType())
                .set(StoreDO::getStoreType, store.getStoreType())
                .set(StoreDO::getBusinessHours, store.getBusinessHours())
                .set(StoreDO::getUpdateBy, store.getUpdateBy())
                .set(StoreDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int updateStatus(Long id, Integer status, Long updateBy)
    {
        return storeMapper.update(null, Wrappers.<StoreDO>lambdaUpdate()
                .eq(StoreDO::getId, id)
                .set(StoreDO::getStatus, status)
                .set(StoreDO::getUpdateBy, updateBy)
                .set(StoreDO::getUpdateTime, LocalDateTime.now()));
    }
}
