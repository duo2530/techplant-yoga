package com.techplant.yoga.coach.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * {@code CoachDO} 与 {@code t_coach} 表结构的对齐测试（教练管理详细设计 §1.2.1、§1.3.1）。
 *
 * <p>这是本模块最重要的一条硬口径的守卫：<b>t_coach 没有 title / status / deleted，
 * 相册列名是 gallery（不是 album_urls），且 CoachDO 不标 {@code @TableLogic}</b>
 * （物理删除、教练无状态，详细设计总览 §2）。任何人把这些旧字段加回来都会被这里挡住。</p>
 *
 * <p>另有两条容易写错的：DO 里 {@code gallery} 必须是 <b>String</b>（承载 JSON），
 * JSON ↔ {@code List<String>} 的转换只在 service 层发生（§3.4 第 2 条）；主键必须是雪花 ID。</p>
 */
@DisplayName("CoachDO 表结构对齐测试")
class CoachDOFieldTest
{
    /** t_coach 的全部业务与审计字段（与 §1.3.1 建表语句一一对应） */
    private static final Set<String> EXPECTED_FIELDS = new LinkedHashSet<String>(Arrays.asList(
            "id", "name", "avatarUrl", "intro", "phone", "gallery",
            "createBy", "createTime", "updateBy", "updateTime"));

    /** 本表刻意不建的字段（旧设计的 title / status / deleted 与旧列名 album_urls） */
    private static final Set<String> FORBIDDEN_FIELDS = new HashSet<String>(Arrays.asList(
            "title", "status", "deleted", "albumUrls", "albumUrlsJson"));

    @Test
    @DisplayName("表名固定为 t_coach")
    void tableName_shouldBeTCoach()
    {
        TableName tableName = CoachDO.class.getAnnotation(TableName.class);

        assertNotNull(tableName, "CoachDO 必须标 @TableName");
        assertEquals("t_coach", tableName.value());
    }

    @Test
    @DisplayName("字段与 t_coach 建表语句完全一致（多一个少一个都算失败）")
    void fields_shouldMatchTargetStructure()
    {
        assertEquals(EXPECTED_FIELDS, declaredFieldNames());
    }

    @Test
    @DisplayName("刻意不建的字段（title / status / deleted / album_urls）一个都不许出现")
    void forbiddenFields_shouldNotExist()
    {
        for (String forbidden : FORBIDDEN_FIELDS)
        {
            assertFalse(declaredFieldNames().contains(forbidden),
                    "t_coach 不应再有字段：" + forbidden);
        }
    }

    @Test
    @DisplayName("物理删除：任何字段都不标 @TableLogic")
    void noField_shouldBeAnnotatedWithTableLogic()
    {
        for (Field field : CoachDO.class.getDeclaredFields())
        {
            assertNull(field.getAnnotation(TableLogic.class),
                    "本表物理删除，字段不允许标 @TableLogic：" + field.getName());
        }
    }

    @Test
    @DisplayName("相册用 String 承载 JSON（列名 gallery），不是 List、不是 album_urls")
    void gallery_shouldBeJsonString()
    {
        Field gallery = field("gallery");

        assertEquals(String.class, gallery.getType(), "DO 的 gallery 必须是 String（JSON 字符串）");
    }

    @Test
    @DisplayName("主键是应用侧生成的雪花 ID（IdType.ASSIGN_ID）")
    void id_shouldUseAssignedSnowflakeId()
    {
        TableId tableId = field("id").getAnnotation(TableId.class);

        assertNotNull(tableId, "主键必须标 @TableId");
        assertEquals("id", tableId.value());
        assertEquals(IdType.ASSIGN_ID, tableId.type(), "雪花 ID 由应用侧生成，不用数据库自增");
    }

    private Field field(String name)
    {
        try
        {
            return CoachDO.class.getDeclaredField(name);
        }
        catch (NoSuchFieldException e)
        {
            throw new AssertionError("字段不存在：" + name, e);
        }
    }

    private Set<String> declaredFieldNames()
    {
        Set<String> names = new LinkedHashSet<String>();
        for (Field field : CoachDO.class.getDeclaredFields())
        {
            if (!field.isSynthetic())
            {
                names.add(field.getName());
            }
        }
        return names;
    }
}
