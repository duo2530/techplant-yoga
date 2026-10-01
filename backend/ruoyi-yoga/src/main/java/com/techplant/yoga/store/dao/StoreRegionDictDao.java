package com.techplant.yoga.store.dao;

import java.util.Map;

/**
 * 门店「所在区域」字典数据访问（只读）。
 *
 * <p>数据来源是若依字典表 {@code sys_dict_data} 中 {@code dict_type = 'store_region'} 且
 * {@code status = '0'}（正常）的记录；本 DAO <b>只有查询</b>（门店管理详细设计 §1.2.2）。</p>
 */
public interface StoreRegionDictDao
{
    /**
     * 查询区域字典的「code → 区名」映射
     *
     * <p>一次取回整张区域字典（16 条），同一请求内的取值校验与区名翻译共用这一份结果，
     * 避免按行反复查库。</p>
     *
     * @return 有序映射，key 为 {@code dict_value}（6 位行政区划代码），value 为 {@code dict_label}（区名，非 null）
     */
    Map<String, String> selectRegionLabelMap();
}
