package com.techplant.yoga.member.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.techplant.yoga.member.domain.MemberDO;
import com.techplant.yoga.member.dto.MemberCreateDTO;
import com.techplant.yoga.member.enums.MemberGenderEnum;
import com.techplant.yoga.member.vo.MemberDetailVO;
import com.techplant.yoga.member.vo.MemberListItemVO;

/**
 * 会员对象转换。
 *
 * <p>字段少，本版手写转换方法；手机号后四位与显示名属业务规则，由 service 生成。
 * 性别名称取自固定枚举，等级名称取自字典 {@code member_level} 的映射，本类不自行取数。</p>
 */
public final class MemberConverter
{
    private MemberConverter()
    {
    }

    /**
     * 新增请求 → 数据对象：主键不赋值（由 MyBatis-Plus 生成雪花ID），
     * 性别缺省取 0，等级由 service 按字典解析缺省值并校验后传入
     */
    public static MemberDO toDO(MemberCreateDTO dto, Integer level)
    {
        MemberDO member = new MemberDO();
        member.setPhone(trim(dto.getPhone()));
        member.setGender(dto.getGender() == null ? MemberGenderEnum.UNKNOWN.getCode() : dto.getGender());
        member.setLevel(level);
        return member;
    }

    /**
     * 数据对象 → 列表项：性别、等级成对返回，显示名空值兜底为空字符串
     */
    public static MemberListItemVO toListItemVO(MemberDO member, Map<Integer, String> levelLabels)
    {
        MemberListItemVO vo = new MemberListItemVO();
        vo.setId(member.getId());
        vo.setPhone(member.getPhone());
        vo.setName(nameOrEmpty(member.getName()));
        vo.setGender(member.getGender());
        vo.setGenderText(MemberGenderEnum.nameOf(member.getGender()));
        vo.setLevel(member.getLevel());
        vo.setLevelText(levelText(member.getLevel(), levelLabels));
        vo.setRegistered(member.isRegistered());
        vo.setCreateTime(member.getCreateTime());
        vo.setUpdateTime(member.getUpdateTime());
        return vo;
    }

    /**
     * 数据对象集合 → 列表项集合
     */
    public static List<MemberListItemVO> toListItemVOList(List<MemberDO> members, Map<Integer, String> levelLabels)
    {
        List<MemberListItemVO> list = new ArrayList<MemberListItemVO>();
        if (members != null)
        {
            for (MemberDO member : members)
            {
                list.add(toListItemVO(member, levelLabels));
            }
        }
        return list;
    }

    /**
     * 数据对象 → 详情：字段与列表项一致
     */
    public static MemberDetailVO toDetailVO(MemberDO member, Map<Integer, String> levelLabels)
    {
        MemberDetailVO vo = new MemberDetailVO();
        vo.setId(member.getId());
        vo.setPhone(member.getPhone());
        vo.setName(nameOrEmpty(member.getName()));
        vo.setGender(member.getGender());
        vo.setGenderText(MemberGenderEnum.nameOf(member.getGender()));
        vo.setLevel(member.getLevel());
        vo.setLevelText(levelText(member.getLevel(), levelLabels));
        vo.setRegistered(member.isRegistered());
        vo.setCreateTime(member.getCreateTime());
        vo.setUpdateTime(member.getUpdateTime());
        return vo;
    }

    /**
     * 等级名称：字典中取不到（等级码已从字典移除）时返回 {@code null}
     */
    private static String levelText(Integer level, Map<Integer, String> levelLabels)
    {
        return level == null || levelLabels == null ? null : levelLabels.get(level);
    }

    /**
     * 显示名兜底：库里不允许为空，空值时返回空字符串而不是 {@code null}
     */
    private static String nameOrEmpty(String name)
    {
        return name == null ? "" : name;
    }

    /**
     * 去除字符串前后空格；空串归一为 {@code null}
     */
    private static String trim(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
