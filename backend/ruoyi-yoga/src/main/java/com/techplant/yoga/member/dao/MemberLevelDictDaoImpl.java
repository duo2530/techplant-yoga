package com.techplant.yoga.member.dao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.techplant.yoga.member.domain.MemberLevelDictDO;
import com.techplant.yoga.member.mapper.MemberLevelDictMapper;

/**
 * 会员等级字典数据访问实现。
 *
 * <p>条件固定为 {@code dict_type = 'member_level'} AND {@code status = '0'}（若依字典 status
 * 的 '0' 表示正常），不提供任何写方法。</p>
 */
@Repository
public class MemberLevelDictDaoImpl implements MemberLevelDictDao
{
    /** 会员等级字典类型 */
    private static final String DICT_TYPE_MEMBER_LEVEL = "member_level";

    /** 若依字典状态：0 正常 */
    private static final String STATUS_NORMAL = "0";

    private final MemberLevelDictMapper memberLevelDictMapper;

    public MemberLevelDictDaoImpl(MemberLevelDictMapper memberLevelDictMapper)
    {
        this.memberLevelDictMapper = memberLevelDictMapper;
    }

    @Override
    public Map<Integer, String> selectLevelLabelMap()
    {
        LambdaQueryWrapper<MemberLevelDictDO> wrapper = Wrappers.<MemberLevelDictDO>lambdaQuery()
                .eq(MemberLevelDictDO::getDictType, DICT_TYPE_MEMBER_LEVEL)
                .eq(MemberLevelDictDO::getStatus, STATUS_NORMAL)
                .orderByAsc(MemberLevelDictDO::getDictSort);
        List<MemberLevelDictDO> dictDatas = memberLevelDictMapper.selectList(wrapper);

        Map<Integer, String> labelMap = new LinkedHashMap<Integer, String>();
        if (dictDatas != null)
        {
            for (MemberLevelDictDO dictData : dictDatas)
            {
                Integer level = parseLevel(dictData.getDictValue());
                if (level == null)
                {
                    continue;
                }
                labelMap.put(level, dictData.getDictLabel() == null ? "" : dictData.getDictLabel());
            }
        }
        return labelMap;
    }

    /**
     * 等级码解析：字典键值不是整数时视为无效字典行，不参与映射
     */
    private Integer parseLevel(String dictValue)
    {
        if (dictValue == null || dictValue.trim().isEmpty())
        {
            return null;
        }
        try
        {
            return Integer.valueOf(dictValue.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
