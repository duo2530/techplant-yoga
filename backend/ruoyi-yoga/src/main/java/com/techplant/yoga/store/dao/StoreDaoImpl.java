package com.techplant.yoga.store.dao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.techplant.yoga.store.domain.StoreDO;
import com.techplant.yoga.store.mapper.StoreMapper;
import com.techplant.yoga.store.query.StorePublicQuery;
import com.techplant.yoga.store.query.StoreQuery;

/**
 * 门店数据访问实现（门店管理详细设计 §3.2、§3.4）。
 *
 * <p>本表<b>没有 deleted 字段</b>，所有查询天然返回全部有效行；删除走物理 {@code DELETE}。</p>
 */
@Repository
public class StoreDaoImpl implements StoreDao
{
    private final StoreMapper storeMapper;

    public StoreDaoImpl(StoreMapper storeMapper)
    {
        this.storeMapper = storeMapper;
    }

    @Override
    public IPage<StoreDO> selectPage(StoreQuery query)
    {
        LambdaQueryWrapper<StoreDO> wrapper = Wrappers.<StoreDO>lambdaQuery()
                .like(hasText(query.getName()), StoreDO::getName, trimToNull(query.getName()))
                .eq(hasText(query.getRegionCode()), StoreDO::getRegionCode, trimToNull(query.getRegionCode()))
                .eq(query.getStoreType() != null, StoreDO::getStoreType, query.getStoreType())
                // 稳定排序（§2.2.1）：先按门店类型，再按 ID 倒序，避免翻页重复或漏项
                .orderByAsc(StoreDO::getStoreType)
                .orderByDesc(StoreDO::getId);
        return storeMapper.selectPage(new Page<StoreDO>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
    }

    @Override
    public IPage<StoreDO> selectPublicPage(StorePublicQuery query)
    {
        String keyword = trimToNull(query.getKeyword());
        LambdaQueryWrapper<StoreDO> wrapper = Wrappers.<StoreDO>lambdaQuery()
                .eq(hasText(query.getRegionCode()), StoreDO::getRegionCode, trimToNull(query.getRegionCode()))
                // keyword 同时模糊匹配门店名称与地址（用户端接口详细设计 §2.1）
                .and(hasText(keyword), item -> item.like(StoreDO::getName, keyword).or().like(StoreDO::getAddress, keyword))
                // 稳定排序（§2.1）：按区再按名，最后按 ID 倒序
                .orderByAsc(StoreDO::getRegionCode)
                .orderByAsc(StoreDO::getName)
                .orderByDesc(StoreDO::getId);
        return storeMapper.selectPage(new Page<StoreDO>(query.pageNumOrDefault(), query.pageSizeOrDefault()), wrapper);
    }

    @Override
    public StoreDO selectById(Long id)
    {
        if (id == null)
        {
            return null;
        }
        return storeMapper.selectById(id);
    }

    @Override
    public StoreDO selectByName(String name)
    {
        if (!hasText(name))
        {
            return null;
        }
        LambdaQueryWrapper<StoreDO> wrapper = Wrappers.<StoreDO>lambdaQuery()
                .eq(StoreDO::getName, name.trim())
                .orderByAsc(StoreDO::getId);
        List<StoreDO> stores = storeMapper.selectList(wrapper);
        return stores == null || stores.isEmpty() ? null : stores.get(0);
    }

    @Override
    public List<StoreDO> selectByIds(Collection<Long> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<StoreDO> wrapper = Wrappers.<StoreDO>lambdaQuery()
                .select(StoreDO::getId, StoreDO::getName)
                .in(StoreDO::getId, ids);
        List<StoreDO> stores = storeMapper.selectList(wrapper);
        return stores == null ? Collections.<StoreDO>emptyList() : stores;
    }

    @Override
    public int insert(StoreDO store)
    {
        return storeMapper.insert(store);
    }

    @Override
    public int updateById(StoreDO store)
    {
        // 全量编辑：显式 SET 每个业务字段，保证 imageUrl 能被清空（MP 的 updateById 会忽略 null）
        return storeMapper.update(null, Wrappers.<StoreDO>lambdaUpdate()
                .eq(StoreDO::getId, store.getId())
                .set(StoreDO::getName, store.getName())
                .set(StoreDO::getStoreType, store.getStoreType())
                .set(StoreDO::getRegionCode, store.getRegionCode())
                .set(StoreDO::getPhone, store.getPhone())
                .set(StoreDO::getBusinessHours, store.getBusinessHours())
                .set(StoreDO::getAddress, store.getAddress())
                .set(StoreDO::getImageUrl, store.getImageUrl())
                .set(StoreDO::getUpdateBy, store.getUpdateBy())
                .set(StoreDO::getUpdateTime, LocalDateTime.now()));
    }

    @Override
    public int deleteById(Long id)
    {
        // 物理删除：本表无 deleted 字段、无 @TableLogic（详细设计总览 §5 决策 1）
        return storeMapper.deleteById(id);
    }

    private boolean hasText(String value)
    {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value)
    {
        return hasText(value) ? value.trim() : null;
    }
}
