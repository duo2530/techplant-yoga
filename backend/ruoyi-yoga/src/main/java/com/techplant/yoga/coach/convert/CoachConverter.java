package com.techplant.yoga.coach.convert;

import java.util.ArrayList;
import java.util.List;
import com.techplant.yoga.coach.domain.CoachDO;
import com.techplant.yoga.coach.dto.CoachCreateDTO;
import com.techplant.yoga.coach.dto.CoachUpdateDTO;
import com.techplant.yoga.coach.vo.CoachDetailVO;
import com.techplant.yoga.coach.vo.CoachListItemVO;
import com.techplant.yoga.coach.vo.CoachSummaryVO;

/**
 * 教练对象转换（教练管理详细设计 §3.2）。
 *
 * <p><b>边界：</b>本类只做字段搬运与请求字段去空格，<b>不做</b> {@code gallery} 的
 * JSON ↔ {@code List<String>} 转换 —— 该转换只在 service 层发生（§3.4 第 2 条），
 * 因此相册的 JSON 字符串（入）与 {@code List<String>}（出）都作为参数传入。</p>
 */
public final class CoachConverter
{
    private CoachConverter() { }

    /** DTO → DO；{@code galleryJson} 由 service 序列化后传入 */
    public static CoachDO toDO(CoachCreateDTO dto, String galleryJson)
    {
        CoachDO coach = new CoachDO();
        coach.setName(trimToNull(dto.getName()));
        coach.setAvatarUrl(trimToNull(dto.getAvatarUrl()));
        coach.setIntro(trimToNull(dto.getIntro()));
        coach.setPhone(trimToNull(dto.getPhone()));
        coach.setGallery(galleryJson);
        return coach;
    }

    /** 全量覆盖 DO 的业务字段；{@code galleryJson} 由 service 序列化后传入 */
    public static void applyUpdate(CoachDO coach, CoachUpdateDTO dto, String galleryJson)
    {
        coach.setName(trimToNull(dto.getName()));
        coach.setAvatarUrl(trimToNull(dto.getAvatarUrl()));
        coach.setIntro(trimToNull(dto.getIntro()));
        coach.setPhone(trimToNull(dto.getPhone()));
        coach.setGallery(galleryJson);
    }

    public static CoachListItemVO toListItemVO(CoachDO coach)
    {
        CoachListItemVO vo = new CoachListItemVO();
        vo.setId(coach.getId());
        vo.setName(coach.getName());
        vo.setAvatarUrl(coach.getAvatarUrl());
        vo.setPhone(coach.getPhone());
        vo.setCreateTime(coach.getCreateTime());
        vo.setUpdateTime(coach.getUpdateTime());
        return vo;
    }

    public static List<CoachListItemVO> toListItemVOList(List<CoachDO> coaches)
    {
        List<CoachListItemVO> result = new ArrayList<CoachListItemVO>();
        if (coaches != null)
        {
            for (CoachDO coach : coaches)
            {
                result.add(toListItemVO(coach));
            }
        }
        return result;
    }

    /** DO → 详情；{@code gallery} 由 service 反序列化后传入 */
    public static CoachDetailVO toDetailVO(CoachDO coach, List<String> gallery)
    {
        CoachDetailVO vo = new CoachDetailVO();
        vo.setId(coach.getId());
        vo.setName(coach.getName());
        vo.setAvatarUrl(coach.getAvatarUrl());
        vo.setPhone(coach.getPhone());
        vo.setIntro(coach.getIntro());
        vo.setGallery(gallery == null ? new ArrayList<String>() : gallery);
        vo.setCreateTime(coach.getCreateTime());
        vo.setUpdateTime(coach.getUpdateTime());
        return vo;
    }

    public static CoachSummaryVO toSummaryVO(CoachDO coach)
    {
        return new CoachSummaryVO(coach.getId(), coach.getName(), coach.getAvatarUrl(), coach.getIntro());
    }

    public static List<CoachSummaryVO> toSummaryVOList(List<CoachDO> coaches)
    {
        List<CoachSummaryVO> result = new ArrayList<CoachSummaryVO>();
        if (coaches != null)
        {
            for (CoachDO coach : coaches)
            {
                result.add(toSummaryVO(coach));
            }
        }
        return result;
    }

    /** 请求字段前后空格由转换层去除（详细设计 §1.2.3 第 1 条） */
    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
