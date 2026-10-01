package com.techplant.yoga.classroom.convert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.techplant.yoga.classroom.domain.ClassroomDO;
import com.techplant.yoga.classroom.dto.ClassroomCreateDTO;
import com.techplant.yoga.classroom.vo.ClassroomListItemVO;
import com.techplant.yoga.classroom.vo.ClassroomSummaryVO;

/**
 * 教室对象转换（教室管理详细设计 §3.2）。
 *
 * <p>字段少，本版手写转换方法；{@code storeName} 由 service 从 {@code StoreService.summaries}
 * 拿到的映射传入，本类不跨模块取数。</p>
 */
public final class ClassroomConverter
{
    private ClassroomConverter()
    {
    }

    /**
     * 新增请求 → 数据对象；<b>主键不赋值</b>（由 MyBatis-Plus 生成雪花ID）
     */
    public static ClassroomDO toDO(ClassroomCreateDTO dto)
    {
        ClassroomDO classroom = new ClassroomDO();
        classroom.setStoreId(dto.getStoreId());
        classroom.setName(trim(dto.getName()));
        return classroom;
    }

    /**
     * 数据对象 → 列表项（{@code storeName} 由调用方按门店名映射补齐）
     */
    public static ClassroomListItemVO toListItemVO(ClassroomDO classroom, Map<Long, String> storeNames)
    {
        ClassroomListItemVO vo = new ClassroomListItemVO();
        vo.setId(classroom.getId());
        vo.setStoreId(classroom.getStoreId());
        vo.setStoreName(storeNames == null ? null : storeNames.get(classroom.getStoreId()));
        vo.setName(classroom.getName());
        vo.setCreateTime(classroom.getCreateTime());
        vo.setUpdateTime(classroom.getUpdateTime());
        return vo;
    }

    /**
     * 数据对象集合 → 列表项集合
     */
    public static List<ClassroomListItemVO> toListItemVOList(List<ClassroomDO> classrooms, Map<Long, String> storeNames)
    {
        List<ClassroomListItemVO> list = new ArrayList<ClassroomListItemVO>();
        if (classrooms != null)
        {
            for (ClassroomDO classroom : classrooms)
            {
                list.add(toListItemVO(classroom, storeNames));
            }
        }
        return list;
    }

    /**
     * 数据对象 → 摘要（跨模块出参）
     */
    public static ClassroomSummaryVO toSummaryVO(ClassroomDO classroom)
    {
        ClassroomSummaryVO vo = new ClassroomSummaryVO();
        vo.setId(classroom.getId());
        vo.setStoreId(classroom.getStoreId());
        vo.setName(classroom.getName());
        return vo;
    }

    /**
     * 数据对象集合 → 摘要集合
     */
    public static List<ClassroomSummaryVO> toSummaryVOList(List<ClassroomDO> classrooms)
    {
        List<ClassroomSummaryVO> list = new ArrayList<ClassroomSummaryVO>();
        if (classrooms != null)
        {
            for (ClassroomDO classroom : classrooms)
            {
                list.add(toSummaryVO(classroom));
            }
        }
        return list;
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
