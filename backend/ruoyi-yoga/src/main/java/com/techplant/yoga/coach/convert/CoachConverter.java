package com.techplant.yoga.coach.convert;

import java.util.ArrayList;
import java.util.List;
import com.alibaba.fastjson2.JSON;
import com.techplant.yoga.coach.domain.CoachDO;
import com.techplant.yoga.coach.dto.CoachCreateDTO;
import com.techplant.yoga.coach.dto.CoachUpdateDTO;
import com.techplant.yoga.coach.vo.CoachCardVO;
import com.techplant.yoga.coach.vo.CoachDetailVO;
import com.techplant.yoga.coach.vo.CoachIdVO;
import com.techplant.yoga.coach.vo.CoachListItemVO;

/** 教练对象转换（详细设计 §3.1）。 */
public final class CoachConverter
{
    private static final int STATUS_ENABLED = 1;
    private static final int NOT_DELETED = 0;

    private CoachConverter() { }

    public static CoachDO toDO(CoachCreateDTO dto)
    {
        CoachDO coach = new CoachDO();
        coach.setIntro(dto.getIntro());
        coach.setName(dto.getName().trim());
        coach.setTitle(dto.getTitle());
        coach.setAvatarUrl(dto.getAvatarUrl());
        coach.setAlbumUrlsJson(toAlbumJson(dto.getAlbumUrls()));
        coach.setStatus(STATUS_ENABLED);
        coach.setDeleted(NOT_DELETED);
        return coach;
    }

    public static void applyUpdate(CoachDO coach, CoachUpdateDTO dto)
    {
        coach.setIntro(dto.getIntro());
        coach.setName(dto.getName().trim());
        coach.setTitle(dto.getTitle());
        coach.setAvatarUrl(dto.getAvatarUrl());
        coach.setAlbumUrlsJson(toAlbumJson(dto.getAlbumUrls()));
    }

    public static CoachListItemVO toListItemVO(CoachDO coach)
    {
        CoachListItemVO vo = new CoachListItemVO();
        vo.setId(coach.getId());
        vo.setName(coach.getName());
        vo.setTitle(coach.getTitle());
        vo.setAvatarUrl(coach.getAvatarUrl());
        vo.setStatus(coach.getStatus());
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

    public static CoachDetailVO toDetailVO(CoachDO coach)
    {
        CoachDetailVO vo = new CoachDetailVO();
        vo.setId(coach.getId());
        vo.setIntro(coach.getIntro());
        vo.setName(coach.getName());
        vo.setTitle(coach.getTitle());
        vo.setAvatarUrl(coach.getAvatarUrl());
        vo.setAlbumUrls(parseAlbumJson(coach.getAlbumUrlsJson()));
        vo.setStatus(coach.getStatus());
        return vo;
    }

    public static CoachCardVO toCardVO(CoachDO coach)
    {
        CoachCardVO vo = new CoachCardVO();
        vo.setId(coach.getId());
        vo.setName(coach.getName());
        vo.setTitle(coach.getTitle());
        vo.setAvatarUrl(coach.getAvatarUrl());
        return vo;
    }

    public static List<CoachCardVO> toCardVOList(List<CoachDO> coaches)
    {
        List<CoachCardVO> result = new ArrayList<CoachCardVO>();
        if (coaches != null)
        {
            for (CoachDO coach : coaches)
            {
                result.add(toCardVO(coach));
            }
        }
        return result;
    }

    public static CoachIdVO toIdVO(Long id)
    {
        return new CoachIdVO(id);
    }

    public static List<String> parseAlbumJson(String json)
    {
        if (!hasText(json))
        {
            return new ArrayList<String>();
        }
        List<String> values = JSON.parseArray(json, String.class);
        return values == null ? new ArrayList<String>() : values;
    }

    private static String toAlbumJson(List<String> albumUrls)
    {
        return albumUrls == null || albumUrls.isEmpty() ? null : JSON.toJSONString(albumUrls);
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.trim().isEmpty();
    }
}
