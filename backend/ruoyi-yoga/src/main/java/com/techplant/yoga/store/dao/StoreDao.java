package com.techplant.yoga.store.dao;

import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.techplant.yoga.store.domain.StoreDO;
import com.techplant.yoga.store.query.StoreQuery;

/** 门店数据访问语义。 */
public interface StoreDao
{
    IPage<StoreDO> selectPage(StoreQuery query);
    List<StoreDO> selectEnabledList();
    StoreDO selectById(Long id);
    int insert(StoreDO store);
    int updateById(StoreDO store);
    int updateStatus(Long id, Integer status, Long updateBy);
}
