package com.techplant.yoga.coach.vo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 教练出参对象的字段契约测试（教练管理详细设计 §2.3.1、§2.3.2）。
 *
 * <p>守卫三条口径：</p>
 * <ol>
 *   <li><b>列表不返回大字段</b>：{@code CoachListItemVO} 只有 id/name/avatarUrl/phone/审计时间，
 *       <b>没有</b> intro 与 gallery（§2.2.1、§4.1）；</li>
 *   <li><b>详情才有相册</b>：{@code CoachDetailVO.gallery} 是 {@code List<String>}，不是 JSON 字符串；</li>
 *   <li><b>教练没有状态</b>：两个 VO 都不允许再出现 status / statusText。</li>
 * </ol>
 */
@DisplayName("教练 VO 字段契约测试")
class CoachVOFieldTest
{
    /** 列表项的全部字段（比详情少 intro 与 gallery） */
    private static final Set<String> EXPECTED_LIST_ITEM_FIELDS = new LinkedHashSet<String>(Arrays.asList(
            "id", "name", "avatarUrl", "phone", "createTime", "updateTime"));

    /** 详情的全部字段 */
    private static final Set<String> EXPECTED_DETAIL_FIELDS = new LinkedHashSet<String>(Arrays.asList(
            "id", "name", "avatarUrl", "phone", "intro", "gallery", "createTime", "updateTime"));

    /** 跨模块摘要的全部字段（排课模块补齐名称头像用） */
    private static final Set<String> EXPECTED_SUMMARY_FIELDS = new LinkedHashSet<String>(Arrays.asList(
            "id", "name", "avatarUrl", "intro"));

    @Test
    @DisplayName("列表项：只有 id/name/avatarUrl/phone/审计时间，不含 intro 与 gallery")
    void listItem_shouldNotCarryBigFields()
    {
        assertEquals(EXPECTED_LIST_ITEM_FIELDS, declaredFieldNames(CoachListItemVO.class));
        assertFalse(declaredFieldNames(CoachListItemVO.class).contains("intro"), "列表不返回 intro");
        assertFalse(declaredFieldNames(CoachListItemVO.class).contains("gallery"), "列表不返回 gallery");
    }

    @Test
    @DisplayName("列表项：既没有 title（头衔）也没有 status（教练无状态）")
    void listItem_shouldNotCarryTitleOrStatus()
    {
        Set<String> fields = declaredFieldNames(CoachListItemVO.class);

        assertFalse(fields.contains("title"), "旧设计的头衔字段必须删掉");
        assertFalse(fields.contains("status"), "教练没有状态");
        assertFalse(fields.contains("statusText"), "教练没有状态");
    }

    @Test
    @DisplayName("详情：含 intro 与 gallery，gallery 是 List<String>（不是 JSON 字符串）")
    void detail_shouldCarryGalleryAsList()
    {
        assertEquals(EXPECTED_DETAIL_FIELDS, declaredFieldNames(CoachDetailVO.class));
        assertEquals(List.class, field(CoachDetailVO.class, "gallery").getType(),
                "出参的 gallery 必须是 List<String>，JSON 转换在 service 层完成");
    }

    @Test
    @DisplayName("详情：没有 title / status")
    void detail_shouldNotCarryTitleOrStatus()
    {
        Set<String> fields = declaredFieldNames(CoachDetailVO.class);

        assertFalse(fields.contains("title"), "旧设计的头衔字段必须删掉");
        assertFalse(fields.contains("status"), "教练没有状态");
    }

    @Test
    @DisplayName("摘要：只有 id/name/avatarUrl/intro（排课模块的冻结契约）")
    void summary_shouldOnlyCarryFrozenContractFields()
    {
        assertEquals(EXPECTED_SUMMARY_FIELDS, declaredFieldNames(CoachSummaryVO.class));
        assertEquals(Long.class, field(CoachSummaryVO.class, "id").getType(),
                "摘要的业务主键要叫 id，才能被 JacksonConfig 序列化成字符串");
    }

    @Test
    @DisplayName("时间字段自己标 @JsonFormat（框架不设 pattern）")
    void timeFields_shouldDeclareJsonFormat()
    {
        assertJsonFormat(field(CoachListItemVO.class, "createTime"));
        assertJsonFormat(field(CoachListItemVO.class, "updateTime"));
        assertJsonFormat(field(CoachDetailVO.class, "createTime"));
        assertJsonFormat(field(CoachDetailVO.class, "updateTime"));
    }

    private void assertJsonFormat(Field field)
    {
        JsonFormat format = field.getAnnotation(JsonFormat.class);

        assertNotNull(format, field.getName() + " 必须标 @JsonFormat");
        assertEquals("yyyy-MM-dd HH:mm:ss", format.pattern());
    }

    private Field field(Class<?> type, String name)
    {
        try
        {
            return type.getDeclaredField(name);
        }
        catch (NoSuchFieldException e)
        {
            throw new AssertionError(type.getSimpleName() + " 缺少字段：" + name, e);
        }
    }

    private Set<String> declaredFieldNames(Class<?> type)
    {
        Set<String> names = new LinkedHashSet<String>();
        for (Field field : type.getDeclaredFields())
        {
            if (!field.isSynthetic())
            {
                names.add(field.getName());
            }
        }
        return names;
    }
}
