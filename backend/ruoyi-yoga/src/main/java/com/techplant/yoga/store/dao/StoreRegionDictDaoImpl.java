package com.techplant.yoga.store.dao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.techplant.yoga.store.domain.StoreRegionDictDO;
import com.techplant.yoga.store.mapper.StoreRegionDictMapper;

/**
 * 门店区域字典数据访问实现（只读，门店管理详细设计 §1.2.2）。
 *
 * <p>条件固定为 {@code dict_type = 'store_region'} AND {@code status = '0'}（若依字典 status
 * 的 '0' 表示正常），不提供任何写方法。</p>
 */
@Repository
public class StoreRegionDictDaoImpl implements StoreRegionDictDao
{
    /** 门店区域字典类型 */
    private static final String DICT_TYPE_STORE_REGION = "store_region";

    /** 若依字典状态：0 正常 */
    private static final String STATUS_NORMAL = "0";

    private final StoreRegionDictMapper storeRegionDictMapper;

    public StoreRegionDictDaoImpl(StoreRegionDictMapper storeRegionDictMapper)
    {
        this.storeRegionDictMapper = storeRegionDictMapper;
    }

    @Override
    public Map<String, String> selectRegionLabelMap()
    {
        LambdaQueryWrapper<StoreRegionDictDO> wrapper = Wrappers.<StoreRegionDictDO>lambdaQuery()
                .eq(StoreRegionDictDO::getDictType, DICT_TYPE_STORE_REGION)
                .eq(StoreRegionDictDO::getStatus, STATUS_NORMAL)
                .orderByAsc(StoreRegionDictDO::getDictCode);
        List<StoreRegionDictDO> dictDatas = storeRegionDictMapper.selectList(wrapper);

        Map<String, String> labelMap = new LinkedHashMap<String, String>();
        if (dictDatas != null)
        {
            for (StoreRegionDictDO dictData : dictDatas)
            {
                if (dictData.getDictValue() == null)
                {
                    continue;
                }
                labelMap.put(dictData.getDictValue(), dictData.getDictLabel() == null ? "" : dictData.getDictLabel());
            }
        }
        return labelMap;
    }
}
