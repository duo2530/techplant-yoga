package com.techplant.yoga.store.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.techplant.yoga.store.domain.StoreRegionDictDO;

/**
 * 门店区域字典 Mapper：只做<b>只读</b>查询（门店管理详细设计 §1.2.2）。
 *
 * <p>本 Mapper 覆盖的是若依系统表 {@code sys_dict_data}，<b>只允许 select</b>：
 * 不新增、不修改、不删除字典数据，字典数据的维护仍然走若依「系统管理／字典管理」。</p>
 */
public interface StoreRegionDictMapper extends BaseMapper<StoreRegionDictDO>
{
}
