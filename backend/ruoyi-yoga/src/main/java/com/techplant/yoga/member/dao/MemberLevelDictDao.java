package com.techplant.yoga.member.dao;

import java.util.Map;

/**
 * 会员等级字典数据访问（只读）。
 *
 * <p>数据来源是若依字典表 {@code sys_dict_data} 中 {@code dict_type = 'member_level'} 且
 * {@code status = '0'}（正常）的记录；本 DAO <b>只有查询</b>。</p>
 */
public interface MemberLevelDictDao {
    /**
     * 查询会员等级字典的「等级码 → 等级名称」映射
     *
     * @return 有序映射，key 为 {@code dict_value} 解析出的等级码，value 为 {@code dict_label}（非 null）
     */
    Map<Integer, String> selectLevelLabelMap();
}
