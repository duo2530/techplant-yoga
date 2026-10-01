package com.techplant.yoga.store.dao;

import java.util.Collection;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.store.domain.StoreDO;
import com.techplant.yoga.store.query.StorePublicQuery;
import com.techplant.yoga.store.query.StoreQuery;

/**
 * 门店数据访问语义（门店管理详细设计 §3.2）。
 *
 * <p>屏蔽 Mapper 细节：筛选条件、字段选择与<b>稳定排序</b>都在这里组装；
 * 删除是<b>物理删除</b>（{@code StoreDO} 不标 {@code @TableLogic}），业务代码不手写 {@code deleted} 条件。</p>
 */
public interface StoreDao
{
    /** 管理端分页：筛选 name（模糊）／regionCode（精确）／storeType，排序 store_type ASC, id DESC */
    IPage<StoreDO> selectPage(StoreQuery query);

    /** 用户端分页：筛选 regionCode（精确）／keyword（同时模糊匹配名称与地址），排序 region_code ASC, name ASC, id DESC */
    IPage<StoreDO> selectPublicPage(StorePublicQuery query);

    /** 按主键查询（物理删除后查不到） */
    StoreDO selectById(Long id);

    /** 按名称精确查询（名称唯一校验用，返回第一条，供 service 排除自身） */
    StoreDO selectByName(String name);

    /** 按主键集合批量查询，仅取 id 与 name（跨模块 summaries 用） */
    List<StoreDO> selectByIds(Collection<Long> ids);

    /** 新增 */
    int insert(StoreDO store);

    /** 全量更新业务字段（显式 SET，保证 imageUrl 能被清空） */
    int updateById(StoreDO store);

    /** 物理删除 */
    int deleteById(Long id);
}
