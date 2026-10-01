package com.techplant.yoga.store.domain;

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
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * {@code StoreDO} 与 {@code t_store} 表结构的对齐测试（门店管理详细设计 §1.2.1、§1.3.1）。
 *
 * <p>这是本模块最重要的一条硬口径的守卫：<b>t_store 没有 status / business_type /
 * province_code / city_code / district_code / deleted，且 StoreDO 不标 {@code @TableLogic}</b>
 * （物理删除，详细设计总览 §5 决策 1）。任何人把这些字段加回来都会被这里挡住。</p>
 */
@DisplayName("StoreDO 表结构对齐测试")
class StoreDOFieldTest
{
    /** t_store 的全部业务与审计字段（与 §1.3.1 建表语句一一对应） */
    private static final Set<String> EXPECTED_FIELDS = new LinkedHashSet<String>(Arrays.asList(
            "id", "name", "storeType", "regionCode", "phone", "businessHours", "address", "imageUrl",
            "createBy", "createTime", "updateBy", "updateTime"));

    /** 本表刻意不建的字段 */
    private static final Set<String> FORBIDDEN_FIELDS = new HashSet<String>(Arrays.asList(
            "status", "businessType", "provinceCode", "cityCode", "districtCode", "region", "deleted"));

    @Test
    @DisplayName("表名固定为 t_store")
    void tableName_shouldBeTStore()
    {
        TableName tableName = StoreDO.class.getAnnotation(TableName.class);

        assertNotNull(tableName, "StoreDO 必须标 @TableName");
        assertEquals("t_store", tableName.value());
    }

    @Test
    @DisplayName("字段与 t_store 建表语句完全一致（多一个少一个都算失败）")
    void fields_shouldMatchTargetStructure()
    {
        assertEquals(EXPECTED_FIELDS, declaredFieldNames());
    }

    @Test
    @DisplayName("刻意不建的字段（含 deleted）一个都不许出现")
    void forbiddenFields_shouldNotExist()
    {
        for (String forbidden : FORBIDDEN_FIELDS)
        {
            assertFalse(declaredFieldNames().contains(forbidden),
                    "t_store 不应再有字段：" + forbidden);
        }
    }

    @Test
    @DisplayName("物理删除：任何字段都不标 @TableLogic")
    void noField_shouldBeAnnotatedWithTableLogic()
    {
        for (Field field : StoreDO.class.getDeclaredFields())
        {
            assertNull(field.getAnnotation(TableLogic.class),
                    "本表物理删除，字段不允许标 @TableLogic：" + field.getName());
        }
    }

    private Set<String> declaredFieldNames()
    {
        Set<String> names = new LinkedHashSet<String>();
        for (Field field : StoreDO.class.getDeclaredFields())
        {
            // 忽略编译器生成的合成字段（如内部类引用），业务 DO 正常不会有
            if (!field.isSynthetic())
            {
                names.add(field.getName());
            }
        }
        return names;
    }
}
